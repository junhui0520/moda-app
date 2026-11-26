"""이미지 입출력 공통 함수 (한글 경로 · 스마트폰 사진 회전 정보 대응)."""
import os

import cv2
import numpy as np
from PIL import Image, ImageOps

import config


def model_path(name):
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), config.MODEL_DIR, name)
    if not os.path.exists(path):
        raise FileNotFoundError(
            f"모델 파일이 없습니다: {path}\n먼저 `python download_models.py` 를 실행하세요."
        )
    return path


def load_rgb(path):
    """EXIF 회전을 반영해서 RGB numpy 배열로 읽음. 한글 경로도 OK."""
    with Image.open(path) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        return np.ascontiguousarray(np.array(im))


def save_bgr(path, bgr):
    """한글 경로에도 저장되도록 imencode 사용."""
    ext = os.path.splitext(path)[1] or ".jpg"
    ok, buf = cv2.imencode(ext, bgr)
    if ok:
        buf.tofile(path)
    return ok


def dist(a, b):
    return float(np.hypot(a[0] - b[0], a[1] - b[1]))


def angle_at(p, a, b):
    """점 p 에서 a, b 를 바라보는 각도(도)."""
    v1 = np.array(a) - np.array(p)
    v2 = np.array(b) - np.array(p)
    cos = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2) + 1e-9)
    return float(np.degrees(np.arccos(np.clip(cos, -1, 1))))
