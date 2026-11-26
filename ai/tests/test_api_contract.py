"""AI 서버 응답이 백엔드 AiResponseParser 규칙(docs/api-spec.md 부록 A.2~A.4)을 지키는지 확인.
사진 분석(/v1/analyze/*)과 실시간 측정 판정(/v1/live/*) 모두 확인합니다.

ai 폴더에서 실행:
    python tests/test_api_contract.py [사진폴더]
사진폴더를 주지 않으면 빈 이미지·깨진 파일 같은 '판정 불가' 경우만 확인합니다.
"""
import io
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from fastapi.testclient import TestClient  # noqa: E402

import server  # noqa: E402

FACE_KEYS = {"diamond", "heart", "oval", "rectangle", "round", "long"}
BODY_KEYS = {"standard", "small_inv_triangle", "large_rectangle", "rectangle"}
SK_ITEMS = {"bideltoid_width", "waist_width", "hip_width", "shoulder_height", "crotch_height", "arm_length"}


def _is_num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def check_candidates(c, keys):
    assert isinstance(c, list) and c, "candidates 가 비어 있음"
    for x in c:
        assert x["key"] in keys and _is_num(x["prob"]) and 0 <= x["prob"] <= 1, x
    probs = [x["prob"] for x in c]
    assert probs == sorted(probs, reverse=True), "candidates 내림차순 아님"
    assert abs(sum(probs) - 1) < 0.02, f"확률 합 {sum(probs)}"


def check_face(r):
    assert isinstance(r["usable"], bool)
    assert isinstance(r["warnings"], list) and all(isinstance(w, str) for w in r["warnings"])
    assert "FACE_BORDERLINE" not in r["warnings"], "BORDERLINE 코드는 백엔드가 추가함"
    if not r["usable"]:
        return
    assert r["face_shape"] in FACE_KEYS
    assert _is_num(r["confidence"]) and 0 <= r["confidence"] <= 1
    assert isinstance(r["borderline"], bool)
    check_candidates(r["candidates"], FACE_KEYS)
    assert r["candidates"][0]["key"] == r["face_shape"], "최종 유형 ≠ 확률 1위"
    assert r["confidence"] == r["candidates"][0]["prob"]
    assert isinstance(r["features"], dict) and all(_is_num(v) for v in r["features"].values())
    for k in ("length_ratio", "forehead_ratio", "jaw_ratio", "chin_ratio", "jaw_angle_deg"):
        assert k in r["features"]
    assert r["reason"] is None or (isinstance(r["reason"], str) and r["reason"].strip())


def check_body(r):
    assert isinstance(r["usable"], bool)
    assert isinstance(r["warnings"], list) and all(isinstance(w, str) for w in r["warnings"])
    if not r["usable"]:
        return
    assert r["body_type"] in BODY_KEYS
    f = r.get("features")
    assert f is None or (isinstance(f, dict) and all(_is_num(v) for v in f.values()))
    sk = r.get("compare_to_size_korea")
    if sk is not None:
        assert isinstance(sk["reference"], str) and sk["reference"]
        assert isinstance(sk["items"], list) and sk["items"]
        for it in sk["items"]:
            assert it["key"] in SK_ITEMS and all(_is_num(it[k]) for k in ("user_mm", "ref_mm", "deviation")), it


def png(arr):
    buf = io.BytesIO()
    Image.fromarray(arr).save(buf, format="PNG")
    return buf.getvalue()


def main(photo_dir=None):
    with TestClient(server.app) as c:
        h = c.get("/health").json()
        assert h["status"] == "ok", h
        print("health:", h)

        blank = png(np.zeros((400, 300, 3), np.uint8))
        r = c.post("/v1/analyze/face", files={"image": ("face.png", blank, "image/png")}, data={"request_id": "t1"}).json()
        check_face(r)
        assert r["usable"] is False and r["warnings"] == ["FACE_NOT_DETECTED"], r
        r = c.post("/v1/analyze/body", files={"image": ("body.png", blank, "image/png")}, data={"height_cm": "175"}).json()
        check_body(r)
        assert r["usable"] is False and r["warnings"] == ["BODY_NOT_DETECTED"], r
        r = c.post("/v1/analyze/face", files={"image": ("x.jpg", b"not an image", "image/jpeg")}).json()
        check_face(r)
        assert r["usable"] is False, r
        print("판정 불가 경우: OK")

        # 실시간(C 방식) 엔드포인트: 휴대폰 페이지가 보내는 형식 그대로
        face_frame = {"length_ratio": 1.21, "forehead_ratio": 0.92, "jaw_ratio": 0.82, "chin_ratio": 0.67, "jaw_angle_deg": 134.0}
        rounds = [[dict(face_frame, length_ratio=1.21 + 0.003 * i) for i in range(6)] for _ in range(2)]
        r = c.post("/v1/live/face", json={"rounds": rounds, "quality": {"yaw": 1.2}}).json()
        check_face(r)
        assert r["usable"] and r["n_rounds"] == 2 and r["n_frames"] == 12, r
        body_frame = {"shoulder": 0.29, "waist": 0.19, "hip": 0.21, "arm": 0.31, "shoulder_height": 0.81, "crotch_height": 0.46}
        rounds = [[dict(body_frame, shoulder=0.29 + 0.001 * i) for i in range(5)] for _ in range(2)]
        r = c.post("/v1/live/body", json={"rounds": rounds, "height_cm": 175}).json()
        check_body(r)
        assert r["usable"] and r["n_rounds"] == 2, r
        rounds[0] = [dict(f, crotch_height=None) for f in rounds[0]]
        rounds[1] = [dict(f, crotch_height=None) for f in rounds[1]]
        r = c.post("/v1/live/body", json={"rounds": rounds, "height_cm": 175}).json()
        check_body(r)
        assert r["usable"] and "BODY_LEGS_CLOSED" in r["warnings"], r
        r = c.post("/v1/live/face", json={"rounds": [[face_frame, face_frame]]}).json()   # 프레임 부족
        check_face(r)
        assert r["usable"] is False and r["warnings"] == ["LIVE_NOT_ENOUGH_FRAMES"], r
        bad = c.post("/v1/live/body", json={"rounds": rounds})   # 키 없음 → 422 (백엔드가 항상 붙여서 보냄)
        assert bad.status_code == 422
        print("실시간 측정 판정: OK")

        if photo_dir:
            for name in sorted(os.listdir(photo_dir)):
                if not name.lower().endswith((".jpg", ".jpeg", ".png")):
                    continue
                with open(os.path.join(photo_dir, name), "rb") as fp:
                    data = fp.read()
                ctype = "image/png" if name.lower().endswith(".png") else "image/jpeg"
                rf = c.post("/v1/analyze/face", files={"image": (name, data, ctype)}).json()
                check_face(rf)
                rb = c.post("/v1/analyze/body", files={"image": (name, data, ctype)}, data={"height_cm": "175"}).json()
                check_body(rb)
                print(f"{name}: 얼굴 {rf.get('face_shape')} ({rf.get('confidence')}) {rf['warnings']} | "
                      f"체형 {rb.get('body_type')} {rb['warnings']}")
    print("모든 응답이 백엔드 규칙을 지킵니다.")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
