"""휴대폰 실시간 측정 결과 → 판정 (run_live.py · run_live_body.py 의 '회차 종합' 부분).

휴대폰(live/ 페이지)은 좋은 프레임에서 잰 값만 회차별로 보내고,
회차별 중앙값 → 회차 중앙값들의 중앙값 → 판정은 여기서 Python 으로 합니다.
그래서 팀 기준(face_population.json / body_population.json)을 바꾸면 앱 업데이트 없이 바로 반영됩니다.
"""
import math

import numpy as np

import api_format
import body_shape as B
import config
import face_shape as F

MIN_FRAMES_PER_ROUND = 3     # run_live*.py 와 같음: 회차에 프레임이 3개 미만이면 그 회차는 버림
MAX_ROUNDS = 5
MAX_FRAMES_PER_ROUND = 30

# run_live_body.FEATURE_KEYS — 회차 중앙값을 내는 체형 값
BODY_KEYS = ["shoulder_dev", "waist_dev", "hip_dev", "trunk_dev", "taper_dev", "arm_dev", "limb_dev",
             "waist_hip_ratio", "shoulder_width_cm", "waist_width_cm", "hip_width_cm",
             "crotch_height_cm", "arm_length_cm"]
FRACTION_KEYS = ["shoulder", "waist", "hip", "arm", "shoulder_height"]   # + crotch_height (없을 수 있음)


def _finite(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)


def _clean_rounds(rounds, keys, optional=()):
    """형식이 맞는 프레임만 남기고, 프레임이 너무 적은 회차는 버림."""
    out = []
    for r in (rounds or [])[:MAX_ROUNDS]:
        frames = []
        for fr in (r or [])[:MAX_FRAMES_PER_ROUND]:
            if not isinstance(fr, dict) or not all(_finite(fr.get(k)) for k in keys):
                continue
            if any(fr.get(k) is not None and not _finite(fr.get(k)) for k in optional):
                continue
            frames.append(fr)
        if len(frames) >= MIN_FRAMES_PER_ROUND:
            out.append(frames)
    return out


# ---------------------------------------------------------------------------
# 얼굴
# ---------------------------------------------------------------------------
def face_live(rounds, quality=None):
    """rounds: [[{length_ratio, forehead_ratio, jaw_ratio, chin_ratio, jaw_angle_deg}, ...], ...] → A.2 응답."""
    rounds = _clean_rounds(rounds, F.FEATURE_KEYS)
    if not rounds:
        return {"usable": False, "face_shape": None, "warnings": ["LIVE_NOT_ENOUGH_FRAMES"],
                "model_version": api_format.FACE_MODEL_VERSION}
    per_round = []
    for frames in rounds:
        med = {k: float(np.median([fr[k] for fr in frames])) for k in F.FEATURE_KEYS}
        per_round.append({"features": med, "key": F.classify(med), "n": len(frames)})
    final = {k: float(np.median([r["features"][k] for r in per_round])) for k in F.FEATURE_KEYS}
    extra = {"n_rounds": len(per_round), "n_frames": sum(r["n"] for r in per_round),
             "round_results": [r["key"] for r in per_round]}
    return api_format.face_result(final, len(per_round), [], dict(quality or {}), extra)


# ---------------------------------------------------------------------------
# 체형
# ---------------------------------------------------------------------------
def body_features(fr, height_cm):
    """휴대폰이 잰 '키 대비 비율' 1프레임 → body_shape.measure() 가 만드는 것과 같은 특징 dict."""
    ref = config.SIZE_KOREA_REF_MM
    h_ref = ref["height"]

    def dev(frac, ref_mm):
        return frac / (ref_mm / h_ref) - 1

    waist_dev = dev(fr["waist"], ref["waist_width"])
    hip_dev = dev(fr["hip"], ref["hip_width"])
    crotch = fr.get("crotch_height")
    ref_trunk_leg = (ref["shoulder_height"] - ref["crotch_height"]) / ref["crotch_height"]
    return {
        "shoulder_width_cm": fr["shoulder"] * height_cm,
        "waist_width_cm": fr["waist"] * height_cm,
        "hip_width_cm": fr["hip"] * height_cm,
        "crotch_height_cm": crotch * height_cm if crotch else None,
        "arm_length_cm": fr["arm"] * height_cm,
        "shoulder_dev": dev(fr["shoulder"], ref["bideltoid_width"]),
        "waist_dev": waist_dev,
        "hip_dev": hip_dev,
        "trunk_dev": (waist_dev + hip_dev) / 2,
        "taper_dev": (fr["waist"] / fr["hip"]) / (ref["waist_width"] / ref["hip_width"]) - 1,
        "arm_dev": dev(fr["arm"], ref["arm_length"]),
        "limb_dev": (((fr["shoulder_height"] - crotch) / crotch) / ref_trunk_leg - 1) if crotch else None,
        "waist_hip_ratio": fr["waist"] / fr["hip"],
    }


def _median_features(fs):
    med = {}
    for k in BODY_KEYS:
        vals = [f[k] for f in fs if f.get(k) is not None]
        med[k] = float(np.median(vals)) if vals else None
    return med


def body_live(rounds, height_cm, quality=None):
    """rounds: [[{shoulder, waist, hip, arm, shoulder_height, crotch_height}, ...], ...] (모두 키 대비 비율) → A.3 응답."""
    rounds = _clean_rounds(rounds, FRACTION_KEYS, optional=("crotch_height",))
    per_round = []
    for frames in rounds:
        fs = [body_features(fr, height_cm) for fr in frames]
        fs = [f for f in fs if B.plausible(f)]          # 사람 몸으로 볼 수 없는 값은 버림 (휴대폰에서도 거름)
        if len(fs) < MIN_FRAMES_PER_ROUND:
            continue
        med = _median_features(fs)
        per_round.append({"features": med, "key": B.classify(med), "n": len(fs)})
    if not per_round:
        return {"usable": False, "body_type": None, "warnings": ["LIVE_NOT_ENOUGH_FRAMES"],
                "model_version": api_format.BODY_MODEL_VERSION}
    final = _median_features([r["features"] for r in per_round])
    key = B.classify(final)
    codes = [] if final.get("crotch_height_cm") is not None else ["BODY_LEGS_CLOSED"]
    extra = {"n_rounds": len(per_round), "n_frames": sum(r["n"] for r in per_round),
             "round_results": [r["key"] for r in per_round]}
    return api_format.body_result(final, key, height_cm, codes, dict(quality or {}), extra)
