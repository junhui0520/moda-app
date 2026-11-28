"""Moda AI 서버 — 얼굴형·체형 분석 API (FastAPI).

백엔드(ExternalAiAnalysisClient)만 이 서버를 호출합니다. 연동 규칙: docs/api-spec.md 부록 A.

    GET  /health               상태 확인
    POST /v1/analyze/face      multipart: image(필수), request_id(선택)              — 사진 1장 분석
    POST /v1/analyze/body      multipart: image(필수), height_cm(필수), request_id(선택)
    POST /v1/live/face         JSON: 휴대폰 실시간 측정값 → 판정                       — 실시간 분석 (C 방식)
    POST /v1/live/body         JSON: 휴대폰 실시간 측정값 + height_cm → 판정
    GET  /live/live.html?mode=face|body   휴대폰 앱 WebView 가 여는 실시간 측정 페이지

실행 (ai 폴더에서):
    uvicorn server:app --host 0.0.0.0 --port 8000

원칙
- 사진은 디스크에 저장하지 않고 메모리에서만 처리합니다 (R-01). 로그에도 사진·특징값을 남기지 않습니다.
- 사진이 판정 불가여도 HTTP 200 + usable=false 로 답합니다. 4xx/5xx 는 서버 자체 오류에만 씁니다.
"""
import io
import logging
import os
import threading
import time
from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.staticfiles import StaticFiles
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel, Field

import api_format
import body_shape as B
import face_shape as F
import live_api

HERE = os.path.dirname(os.path.abspath(__file__))

VERSION = "0.1.0"
MAX_IMAGE_SIDE = 2048   # 긴 변이 이보다 크면 줄여서 분석 (휴대폰 원본 4000px 대 → 속도·메모리 절약)

log = logging.getLogger("moda.ai")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

# MediaPipe 분석기는 여러 요청이 동시에 쓰면 안전하지 않아서 한 번에 하나씩 처리
_lock = threading.Lock()


def _decode(data):
    """업로드 바이트 → RGB 배열. 스마트폰 사진의 회전(EXIF) 반영. 읽을 수 없으면 None."""
    try:
        with Image.open(io.BytesIO(data)) as im:
            im = ImageOps.exif_transpose(im).convert("RGB")
            if max(im.size) > MAX_IMAGE_SIDE:
                im.thumbnail((MAX_IMAGE_SIDE, MAX_IMAGE_SIDE), Image.LANCZOS)
            return np.ascontiguousarray(np.array(im))
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        return None


def _warm_up():
    """첫 요청이 모델 로딩 때문에 늦어지지 않게 미리 한 번 돌려 둠 (백엔드 읽기 타임아웃 15초)."""
    blank = np.zeros((256, 256, 3), np.uint8)
    t = time.perf_counter()
    with _lock:
        F.population()
        B.body_population()
        F.analyze_face_rgb(blank, annotate=False)
        B.analyze_body_rgb(blank, 175, annotate=False)
    log.info("모델 준비 완료 (%.1fs) — 얼굴 기준 %s / 체형 기준 %s",
             time.perf_counter() - t, api_format.FACE_MODEL_VERSION, api_format.BODY_MODEL_VERSION)


@asynccontextmanager
async def lifespan(_app):
    _warm_up()
    yield


app = FastAPI(title="Moda AI", version=VERSION, lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "version": VERSION,
            "face_model": api_format.FACE_MODEL_VERSION, "body_model": api_format.BODY_MODEL_VERSION}


@app.post("/v1/analyze/face")
def analyze_face(image: UploadFile = File(...), request_id: str | None = Form(None)):
    t = time.perf_counter()
    rgb = _decode(image.file.read())
    if rgb is None:
        res = {"usable": False, "face_shape": None, "warnings": ["IMAGE_UNREADABLE"],
               "model_version": api_format.FACE_MODEL_VERSION}
    else:
        with _lock:
            out, _ = F.analyze_face_rgb(rgb, annotate=False)
            res = api_format.face_response(out)
        del rgb
    log.info("face request_id=%s usable=%s shape=%s warnings=%s %.0fms", request_id, res["usable"],
             res.get("face_shape"), res["warnings"], (time.perf_counter() - t) * 1000)
    return res


@app.post("/v1/analyze/body")
def analyze_body(image: UploadFile = File(...), height_cm: int = Form(...), request_id: str | None = Form(None)):
    t = time.perf_counter()
    rgb = _decode(image.file.read())
    if rgb is None:
        res = {"usable": False, "body_type": None, "warnings": ["IMAGE_UNREADABLE"],
               "model_version": api_format.BODY_MODEL_VERSION}
    else:
        with _lock:
            out, _ = B.analyze_body_rgb(rgb, height_cm, annotate=False)
            res = api_format.body_response(out, height_cm)
        del rgb
    log.info("body request_id=%s usable=%s type=%s warnings=%s %.0fms", request_id, res["usable"],
             res.get("body_type"), res["warnings"], (time.perf_counter() - t) * 1000)
    return res


# ---------------------------------------------------------------------------
# 실시간 분석 (C 방식): 휴대폰 live/ 페이지가 잰 값 → 판정
# ---------------------------------------------------------------------------
Frame = dict[str, float | None]


class LiveFaceRequest(BaseModel):
    rounds: list[list[Frame]] = Field(..., description="회차별 프레임 측정값 (얼굴 특징 5개)")
    quality: dict[str, float] | None = None
    request_id: str | None = None


class LiveBodyRequest(BaseModel):
    height_cm: int = Field(..., ge=100, le=250)
    rounds: list[list[Frame]] = Field(..., description="회차별 프레임 측정값 (키 대비 비율)")
    quality: dict[str, float] | None = None
    request_id: str | None = None


@app.post("/v1/live/face")
def live_face(req: LiveFaceRequest):
    t = time.perf_counter()
    with _lock:
        res = live_api.face_live(req.rounds, req.quality)
    log.info("live-face request_id=%s usable=%s shape=%s rounds=%s %.0fms", req.request_id, res["usable"],
             res.get("face_shape"), res.get("round_results"), (time.perf_counter() - t) * 1000)
    return res


@app.post("/v1/live/body")
def live_body(req: LiveBodyRequest):
    t = time.perf_counter()
    with _lock:
        res = live_api.body_live(req.rounds, req.height_cm, req.quality)
    log.info("live-body request_id=%s usable=%s type=%s rounds=%s %.0fms", req.request_id, res["usable"],
             res.get("body_type"), res.get("round_results"), (time.perf_counter() - t) * 1000)
    return res


# 실시간 측정 페이지와 그 페이지가 내려받는 MediaPipe 모델 (먼저 등록한 경로가 우선)
app.mount("/live/models", StaticFiles(directory=os.path.join(HERE, "models"), check_dir=False), name="live-models")
app.mount("/live", StaticFiles(directory=os.path.join(HERE, "live"), html=True), name="live")
