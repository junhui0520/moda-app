"""분석 결과(face_shape.py / body_shape.py) → 백엔드가 받는 JSON 형식 (docs/api-spec.md 부록 A.2 ~ A.5).

백엔드 AiResponseParser 가 검사하는 규칙:
- 얼굴: usable·face_shape·warnings 필수. usable=true 이면 confidence·borderline·candidates·features 도 필수.
- 체형: usable·body_type·warnings 만 필수, 나머지는 선택 (우리는 확신도·후보까지 모두 보냄).
- face_shape / body_type 은 정해진 영문 Key 만 허용 (config.FACE_LABELS / BODY_LABELS 의 키와 같음).
- warnings 에는 코드 문자열만 넣음. 메시지·심각도(RETAKE/INFO)는 백엔드가 붙임.
- FACE_BORDERLINE / BODY_BORDERLINE 은 백엔드가 borderline=true 를 보고 직접 추가하므로 여기서는 넣지 않음.
"""
import json
import os

import config
import face_shape as F
import body_shape as B

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------------------
# 체형 촬영 문제 코드(body_shape.ISSUES) → 백엔드 경고 코드 (A.5)
# 백엔드에 없는 코드(BODY_NOT_FRONTAL 등)는 '촬영 상태를 한 번 더 확인해 주세요'(INFO)로 표시되고,
# usable=false 인데 RETAKE 코드가 없으면 백엔드가 ANALYSIS_UNUSABLE(재촬영 안내)을 추가합니다.
# ---------------------------------------------------------------------------
BODY_WARNING_CODES = {
    "no_person": "BODY_NOT_DETECTED",
    "multi": "MULTIPLE_PEOPLE",
    "feet_hidden": "BODY_LOW_VISIBILITY",
    "body_hidden": "BODY_LOW_VISIBILITY",
    "head_cut": "BODY_LOW_VISIBILITY",
    "feet_cut": "BODY_LOW_VISIBILITY",
    "tilt": "SHOULDER_TILT",
    "not_standing": "BODY_NOT_STANDING",     # 백엔드 미정의 (앉은 자세 등)
    "no_mask": "BODY_MEASURE_FAILED",        # 백엔드 미정의 (사람 영역 분리 실패)
    "width_fail": "BODY_MEASURE_FAILED",     # 백엔드 미정의 (어깨·허리·골반 폭 측정 실패)
    "implausible": "BODY_POSE_INVALID",      # 백엔드 미정의 (팔을 옆으로 벌려 폭이 비정상)
    "side": "BODY_NOT_FRONTAL",              # 백엔드 미정의 (몸이 옆으로 돌아감)
    "arms": "BODY_ARMS_CLOSE",               # 백엔드 미정의 (팔이 몸통에 붙음 — 분석은 진행)
    "legs": "BODY_LEGS_CLOSED",              # 백엔드 미정의 (다리 간격 없음 — 샅높이만 생략)
}

# 얼굴 reason 문장용 (style-rules.md 7장: 외모 평가 배제, 중립·장점 중심 표현)
_FEATURE_PHRASE = {   # 키: (평균보다 클 때, 작을 때) — 각각 (이어지는 꼴, 끝나는 꼴)
    "length_ratio": (("얼굴 길이 비율이 높고", "얼굴 길이 비율이 높아"), ("얼굴 길이 비율이 낮고", "얼굴 길이 비율이 낮아")),
    "forehead_ratio": (("이마 너비 비율이 높고", "이마 너비 비율이 높아"), ("이마 너비 비율이 낮고", "이마 너비 비율이 낮아")),
    "jaw_ratio": (("턱 너비 비율이 높고", "턱 너비 비율이 높아"), ("턱 너비 비율이 낮고", "턱 너비 비율이 낮아")),
    "chin_ratio": (("턱끝 너비 비율이 높고", "턱끝 너비 비율이 높아"), ("턱끝 너비 비율이 낮고", "턱끝 너비 비율이 낮아")),
    "jaw_angle_deg": (("턱선이 부드럽게 이어지고", "턱선이 부드럽게 이어져"), ("턱선이 또렷하고", "턱선이 또렷해")),
}
_TYPE_PHRASE = {
    "diamond": "광대 라인이 또렷하게 살아나는 다이아몬드형 특징이 보입니다.",
    "heart": "이마에서 턱끝으로 갸름하게 모이는 하트형 특징이 보입니다.",
    "oval": "전체 비율이 고르게 어우러지는 계란형 특징이 보입니다.",
    "rectangle": "윤곽이 반듯하게 떨어지는 직사각형 특징이 보입니다.",
    "round": "부드러운 곡선이 살아나는 둥근형 특징이 보입니다.",
    "long": "세로 라인이 시원하게 이어지는 긴형 특징이 보입니다.",
}
REASON_Z_MIN = 0.8   # 평균 대비 이만큼(표준편차 배수) 벗어난 특징만 문장에 씀
REASON_MAX_ITEMS = 2


def _version(kind, filename):
    """model_version 문자열 — 어떤 팀 기준 파일로 판정했는지 로그에서 알 수 있게."""
    path = os.path.join(HERE, filename)
    try:
        with open(path, encoding="utf-8") as fp:
            data = json.load(fp)
        created = str(data.get("created", "")).split(" ")[0].replace("-", "")
        return f"{kind}-relative-{created}-n{data.get('n_people', '?')}"
    except (OSError, ValueError):
        return f"{kind}-rules"


FACE_MODEL_VERSION = _version("face", "face_population.json")
BODY_MODEL_VERSION = _version("body", "body_population.json")


def face_reason(key, features):
    """평균 대비 특징 요약 문장 (api-spec v8 `reason`). 계란형이거나 두드러진 특징이 없으면 균형 문장."""
    if config.FACE_CLASSIFIER == "rules":
        return None
    z = F.z_scores(features)
    items = sorted(((abs(v), k, v) for k, v in z.items() if abs(v) >= REASON_Z_MIN), reverse=True)[:REASON_MAX_ITEMS]
    if not items:
        return "평균에 가까운 고른 비율로, " + _TYPE_PHRASE[key]
    parts = []
    for i, (_, k, v) in enumerate(items):
        connect, end = _FEATURE_PHRASE[k][0 if v > 0 else 1]
        parts.append(end if i == len(items) - 1 else connect)
    return "평균 대비 " + " ".join(parts) + ", " + _TYPE_PHRASE[key]


def _unique(codes):
    seen, out = set(), []
    for c in codes:
        if c not in seen:
            seen.add(c)
            out.append(c)
    return out


def face_response(out):
    """face_shape.analyze_face_rgb() 결과 → A.2 응답."""
    codes = _unique(out.get("warning_codes", []))
    quality = {}
    if "head_pose_deg" in out:
        quality["yaw_deg"] = out["head_pose_deg"]["yaw"]
        quality["pitch_deg"] = out["head_pose_deg"]["pitch"]
    if "face_width_px" in out:
        quality["face_width_px"] = out["face_width_px"]
    if "expression" in out:
        quality["jaw_open"] = out["expression"]["jaw_open"]
        quality["smile"] = out["expression"]["smile"]

    if not out.get("ok") or not out.get("usable"):
        return {
            "usable": False, "face_shape": None, "reason": None, "confidence": None, "borderline": None,
            "candidates": None, "warnings": codes or ["FACE_NOT_DETECTED"], "features": None,
            "compare_to_size_korea": None, "quality": quality, "model_version": FACE_MODEL_VERSION,
        }

    feats = {k: float(out["features"][k]) for k in F.FEATURE_KEYS}
    return face_result(feats, 1, codes, quality)


def face_result(feats, n_photos, codes, quality, extra=None):
    """특징값 5개 → usable=true 얼굴 응답. n_photos = 중앙값에 쓴 측정 횟수 (사진 1장=1, 실시간 2회차=2)."""
    # 확률은 6종 모두 내려줌 (0% 포함, 내림차순). summarize() 는 화면용으로 상위 3개만 남기므로 다시 계산.
    probs = dict(F.probabilities(feats, n_photos=n_photos))
    candidates = sorted(({"key": k, "prob": round(probs.get(k, 0.0), 3)} for k in config.FACE_LABELS),
                        key=lambda c: -c["prob"])
    key = candidates[0]["key"]
    confidence = candidates[0]["prob"]
    return {
        "usable": True,
        "face_shape": key,
        "reason": face_reason(key, feats),
        "confidence": confidence,
        "borderline": confidence < config.FACE_MIN_CONFIDENCE,
        "candidates": candidates,
        "warnings": codes,
        "features": {k: round(v, 3) for k, v in feats.items()},
        "compare_to_size_korea": None,
        "quality": quality,
        "model_version": FACE_MODEL_VERSION,
        **(extra or {}),
    }


def _size_korea(f, height_cm):
    """Size Korea 20대 남성 평균과 항목별 비교 (A.3). 사진에서 잰 항목만 넣음 (어깨높이는 재지 않음)."""
    ref = config.SIZE_KOREA_REF_MM
    h_mm = height_cm * 10.0

    def item(key, user_cm):
        if user_cm is None:
            return None
        user_mm = user_cm * 10.0
        return {"key": key, "user_mm": round(user_mm), "ref_mm": ref[key],
                "deviation": round((user_mm / h_mm) / (ref[key] / ref["height"]) - 1, 3)}

    items = [
        item("bideltoid_width", f.get("shoulder_width_cm")),
        item("waist_width", f.get("waist_width_cm")),
        item("hip_width", f.get("hip_width_cm")),
        item("crotch_height", f.get("crotch_height_cm")),
        item("arm_length", f.get("arm_length_cm")),
    ]
    return {
        "reference": "SIZE_KOREA_20S_MALE",
        "height_cm": height_cm,
        "items": [x for x in items if x is not None],
        "summary": {"shoulder_deviation": round(f["shoulder_dev"], 3), "trunk_deviation": round(f["trunk_dev"], 3)},
    }


def body_response(out, height_cm):
    """body_shape.analyze_body_rgb() 결과 → A.3 응답."""
    codes = _unique(BODY_WARNING_CODES.get(c, "BODY_" + c.upper()) for c in out.get("issues", []))
    quality = {}
    q = out.get("quality", {})
    if "min_visibility" in q:
        quality["min_visibility"] = q["min_visibility"]
    if "tilt" in q:
        quality["shoulder_tilt_deg"] = q["tilt"]

    if not out.get("ok"):
        return {
            "usable": False, "body_type": None, "warnings": codes or ["BODY_NOT_DETECTED"],
            "quality": quality, "model_version": BODY_MODEL_VERSION,
        }

    return body_result(out["features"], 1, height_cm, codes, quality)


def body_result(f, n_photos, height_cm, codes, quality, extra=None):
    """측정값 dict(body_shape.measure 의 f) → usable=true 체형 응답.
    n_photos = 중앙값에 쓴 측정 횟수 (사진 1장=1, 실시간 2회차=2). 최종 체형 = 확률 1위 (얼굴과 같음)."""
    probs = dict(B.probabilities(f, n_photos=n_photos))
    candidates = sorted(({"key": k, "prob": round(probs.get(k, 0.0), 3)} for k in config.BODY_LABELS),
                        key=lambda c: -c["prob"])
    confidence = candidates[0]["prob"]
    return {
        "usable": True,
        "body_type": candidates[0]["key"],
        "confidence": confidence,
        "borderline": confidence < config.BODY_MIN_CONFIDENCE,   # true 면 백엔드가 BODY_BORDERLINE 안내를 붙임
        "candidates": candidates,
        "warnings": codes,
        "features": {
            "shoulder_dev": round(f["shoulder_dev"], 3),
            "trunk_dev": round(f["trunk_dev"], 3),
            "shoulder_minus_trunk": round(f["shoulder_dev"] - f["trunk_dev"], 3),
        },
        "compare_to_size_korea": _size_korea(f, height_cm),
        "quality": quality,
        "model_version": BODY_MODEL_VERSION,
        **(extra or {}),
    }
