"""
체형 분석 (MediaPipe Pose Landmarker + 사람 영역 마스크, 무료 · 내 컴퓨터에서만 실행)

흐름: 전신 정면 사진 + 키(cm) → 관절 33개 + 사람 실루엣 마스크
     → 어깨/허리/엉덩이 정면 폭, 샅높이, 팔 길이 측정
     → 키 대비 비율을 Size Korea 20대 남성 평균과 비교 → 임시 규칙으로 Pattern 판정

주의: 사진 한 장으로 재는 건 '정면 폭'이지 줄자로 재는 '둘레'가 아닙니다.
"""
import math

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

import config
from common import load_rgb, model_path

NOSE = 0
L_SH, R_SH = 11, 12
L_EL, R_EL = 13, 14
L_WR, R_WR = 15, 16
L_IDX, R_IDX = 19, 20
L_HIP, R_HIP = 23, 24
L_KNEE, R_KNEE = 25, 26
L_ANK, R_ANK = 27, 28
L_HEEL, R_HEEL = 29, 30
REQUIRED = [L_SH, R_SH, L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANK, R_ANK]

_landmarker = None


def _get_landmarker():
    global _landmarker
    if _landmarker is None:
        opts = vision.PoseLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_path=model_path(config.POSE_MODEL)),
            running_mode=vision.RunningMode.IMAGE,
            num_poses=2,
            output_segmentation_masks=True,
        )
        _landmarker = vision.PoseLandmarker.create_from_options(opts)
    return _landmarker


def make_video_landmarker():
    """실시간(영상) 모드용 — 프레임마다 timestamp 를 넣어 호출."""
    opts = vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path(config.POSE_MODEL)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=2,
        output_segmentation_masks=True,
    )
    return vision.PoseLandmarker.create_from_options(opts)


def _run_at(mask_row, cx):
    """마스크의 한 줄에서 x=cx 를 포함하는 연속 구간 (왼쪽, 오른쪽)."""
    cx = int(np.clip(cx, 0, len(mask_row) - 1))
    if not mask_row[cx]:
        return None
    l = cx
    while l > 0 and mask_row[l - 1]:
        l -= 1
    r = cx
    while r < len(mask_row) - 1 and mask_row[r + 1]:
        r += 1
    return l, r


def classify(f):
    """판정 방식 선택: config.BODY_CLASSIFIER = "relative"(기본) 또는 "rules"."""
    if config.BODY_CLASSIFIER == "rules" or not relative_ready():
        return _classify_rules(f)
    return _classify_relative(f)


def relative_ready():
    """상대 판정은 팀 데이터 기준(body_population.json)이 있어야 의미가 있음.
    없으면(임시 기준이면) 사진 측정 치우침 때문에 오히려 한쪽으로 쏠려서 예전 규칙으로 대신 판정."""
    return config.BODY_RELATIVE_WITHOUT_FIT or "임시" not in body_population()["source"]


def _classify_rules(f):
    """(예전 방식) Size Korea 평균 대비 절대 편차 기준."""
    r = config.BODY_RULES
    sh, tr = f["shoulder_dev"], f["trunk_dev"]
    if abs(sh) <= r["standard_tol"] and abs(tr) <= r["standard_tol"]:
        return "standard"
    if tr >= r["large_rect_min_trunk"]:
        return "large_rectangle"
    if tr <= r["small_inv_max_trunk"] and sh - tr >= r["small_inv_min_shoulder_gap"]:
        return "small_inv_triangle"
    return "rectangle"


_classify = classify   # 예전 이름 호환


def body_z(f):
    """편차 값 → 팀 데이터 평균 체형 대비 '표준편차 몇 배' (+면 평균보다 큼)."""
    pop = body_population()
    vals = {"shoulder_dev": f["shoulder_dev"], "trunk_dev": f["trunk_dev"],
            "upper_taper": f["shoulder_dev"] - f["trunk_dev"]}
    return {k: (v - pop["median"][k]) / max(pop["spread"][k], 1e-6) for k, v in vals.items()}


def _classify_relative(f):
    r = body_population()["rules"]
    z = body_z(f)
    if math.hypot(z["shoulder_dev"], z["trunk_dev"]) <= r["standard_radius"]:
        return "standard"
    if z["trunk_dev"] >= r["large_rect_min_trunk_z"]:
        return "large_rectangle"
    if z["upper_taper"] >= r["small_inv_min_taper_z"]:
        return "small_inv_triangle"
    return "rectangle"


_BODY_POP = None


def body_population():
    """평균 체형 기준. fit_body_population.py 가 만든 body_population.json 이 있으면 우선 사용."""
    global _BODY_POP
    if _BODY_POP is None:
        import json
        import os
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "body_population.json")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fp:
                d = json.load(fp)
            _BODY_POP = {"median": d["median"], "spread": d["spread"], "rules": d["rules"], "source": path,
                         "noise": d.get("noise") or dict(config.BODY_FEATURE_NOISE)}
        else:
            _BODY_POP = {"median": config.BODY_POP_MEDIAN, "spread": config.BODY_POP_SPREAD,
                         "rules": config.BODY_REL_RULES, "source": "config.py (임시값)",
                         "noise": dict(config.BODY_FEATURE_NOISE)}
    return _BODY_POP


def noise_scale(n_rounds):
    """n회 측정의 중앙값을 쓰면 흔들림이 줄어드는 비율 (face_shape.noise_scale 과 같음)."""
    return 1.0 if n_rounds <= 1 else 1.2533 / math.sqrt(n_rounds)


def probabilities(f, n=400, seed=0, n_photos=1):
    """측정 오차만큼 어깨·몸통 편차를 흔들어 n번 판정 → 체형별 확률 (얼굴형 probabilities 와 같은 방식).
    확률이 한 체형에 몰리지 않으면 '두 체형의 경계'라는 뜻. n_photos: 몇 회 측정의 중앙값인지."""
    rng = np.random.default_rng(seed)
    pop = body_population()
    noise = pop.get("noise") or config.BODY_FEATURE_NOISE
    s = noise_scale(n_photos)
    counts = {}
    for _ in range(n):
        g = {"shoulder_dev": f["shoulder_dev"] + rng.normal(0, noise["shoulder_dev"] * s),
             "trunk_dev": f["trunk_dev"] + rng.normal(0, noise["trunk_dev"] * s)}
        key = classify(g)
        counts[key] = counts.get(key, 0) + 1
    return sorted(((k, v / n) for k, v in counts.items()), key=lambda x: -x[1])


def _relative_reason(z):
    parts = []
    if z["trunk_dev"] >= 0.6:
        parts.append("몸통이 굵은 편")
    elif z["trunk_dev"] <= -0.6:
        parts.append("몸통이 가는 편")
    if z["upper_taper"] >= 0.6:
        parts.append("몸통에 비해 어깨가 넓은 편")
    elif z["upper_taper"] <= -0.6:
        parts.append("어깨와 몸통 폭이 비슷한 편")
    if not parts:
        parts.append("어깨·몸통이 평균에 가까운 편")
    return ", ".join(parts)


def _describe(dev, name):
    if abs(dev) < 0.03:
        return f"{name}: 평균 수준"
    return f"{name}: 평균보다 {abs(dev) * 100:.0f}% {'큼' if dev > 0 else '작음'}"


# ---------------------------------------------------------------------------
# 촬영 문제 코드: (치명적이면 True → 측정 불가, 사진용 안내, 실시간 짧은 안내)
# ---------------------------------------------------------------------------
ISSUES = {
    "no_person":   (True,  "사람을 찾지 못했습니다.", "전신이 보이도록 서 주세요"),
    "multi":       (False, "사람이 2명 이상 감지됨 — 가장 크게 찍힌 사람만 분석 (혼자 찍은 사진 권장)",
                    "한 명만 화면에 나와 주세요"),
    "feet_hidden": (True,  "어깨·골반·무릎·발목이 모두 보여야 합니다. 머리부터 발끝까지 나오게 다시 촬영하세요.",
                    "발끝까지 보이게 뒤로 가 주세요"),
    "body_hidden": (True,  "어깨·골반·무릎·발목이 모두 보여야 합니다. 머리부터 발끝까지 나오게 다시 촬영하세요.",
                    "몸 전체가 화면에 들어오게 서 주세요"),
    "not_standing": (True, "선 자세가 아닌 것 같습니다 (앉아 있거나 다리를 굽힘). 똑바로 선 전신 사진이 필요합니다.",
                     "똑바로 서 주세요"),
    "no_mask":     (True,  "사람 영역을 찾지 못했습니다 (배경과 옷 색이 비슷하면 발생).", "배경이 단순한 곳에 서 주세요"),
    "width_fail":  (True,  "어깨/허리/골반 폭을 재지 못했습니다.", "카메라 정면으로 서 주세요"),
    "implausible": (True,  "어깨/몸통 폭이 사람 몸으로 보기 어려운 값으로 재졌습니다 "
                           "(팔을 옆으로 벌려 팔까지 어깨로 잡혔거나 배경이 몸으로 잡힘). 팔을 몸에서 살짝만 뗀 A자 자세로 다시 촬영하세요.",
                    "팔은 옆으로 벌리지 말고 살짝만 떼 주세요"),
    "tilt":        (False, "어깨가 기울어 있음 — 똑바로 선 자세 권장", "몸을 똑바로 세워 주세요"),
    "side":        (False, "몸이 옆으로 돌아가 있는 것 같음 — 정면 사진 필요", "카메라 정면으로 서 주세요"),
    "head_cut":    (False, "머리 윗부분이 사진에 잘렸을 수 있음 — 키 환산이 부정확해짐", "머리 위가 잘렸어요 — 조금 뒤로 가 주세요"),
    "feet_cut":    (False, "발끝이 사진 아래에 붙어 있음 — 발 밑에 여백을 두고 촬영 권장", "발 밑에 여백이 필요해요 — 조금 뒤로 가 주세요"),
    "arms":        (False, "팔이 몸통에 붙어 있음 — 팔 영역을 빼고 쟀지만 오차가 생길 수 있음. 팔을 몸에서 살짝 뗀 A자 자세 권장",
                    "팔을 몸에서 살짝 떼 주세요 (A자 자세)"),
    "legs":        (False, "다리 사이 간격을 찾지 못해 샅높이·다리 비율은 계산하지 않음 — 바지를 입고 다리를 살짝 벌린 채 촬영 권장",
                    "다리를 살짝 벌리면 더 정확해요"),
}


def summarize_body(f, n_photos=1):
    """특징값 → 판정 결과 (사진·실시간·여러 프레임 종합 공용).
    최종 체형 = 확률 1위 (얼굴형과 같음) → 화면의 체형과 확률이 항상 일치.
    n_photos: 몇 회 측정의 중앙값인지 (사진 1장 = 1, 실시간 2회차 = 2)."""
    probs = probabilities(f, n_photos=n_photos)
    key = probs[0][0]
    prob_out = {
        "confidence": round(probs[0][1], 2),
        "candidates": [{"key": k, "body_shape": config.BODY_LABELS[k], "prob": round(p, 2)} for k, p in probs[:3]],
    }
    if probs[0][1] < config.BODY_MIN_CONFIDENCE and len(probs) > 1:
        prob_out["borderline"] = (f"{config.BODY_LABELS[key]}과 {config.BODY_LABELS[probs[1][0]]}의 경계에 가까운 체형 "
                                  f"(확률 {probs[0][1]:.0%} vs {probs[1][1]:.0%})")
    limb = f.get("limb_dev")
    extra = {}
    if config.BODY_CLASSIFIER != "rules" and relative_ready():
        z = body_z(f)
        extra = {"vs_team_average": {k: round(v, 2) for k, v in z.items()}, "reason": _relative_reason(z),
                 "basis": "팀 데이터 평균 기준 (상대 판정)"}
    elif config.BODY_CLASSIFIER != "rules":
        extra = {"basis": "팀 데이터 기준이 없어 예전 규칙(Size Korea 절대 기준)으로 판정 — "
                          "fit_body_population.py --save 로 팀 기준을 만들면 상대 판정으로 바뀝니다"}
    return {
        **extra,
        "body_shape_key": key,
        "body_shape": config.BODY_LABELS[key],
        **prob_out,
        "compare_to_size_korea": [
            _describe(f["shoulder_dev"], "어깨 폭"),
            _describe(f["trunk_dev"], "몸통 굵기"),
            _describe(f["arm_dev"], "팔 길이"),
            "다리 비율: " + ("측정 안 됨" if limb is None else
                           "다리가 긴 편(긴 지체)" if limb < -0.03 else
                           "몸통이 긴 편(짧은 지체)" if limb > 0.03 else "평균 수준"),
        ],
    }


def measure(result, h, w, height_cm):
    """Pose 결과 1개 → (문제 코드 목록, 특징 dict 또는 None, 그리기용 정보). 사진·실시간 공용."""
    issues, geo = [], {}
    if not result.pose_landmarks:
        return ["no_person"], None, geo

    # 여러 명이면 화면에서 가장 크게(어깨~발목이 길게) 잡힌 사람을 분석
    def span(lm):
        return max(lm[L_ANK].y, lm[R_ANK].y) - min(lm[L_SH].y, lm[R_SH].y)
    idx = max(range(len(result.pose_landmarks)), key=lambda i: span(result.pose_landmarks[i]))
    if len(result.pose_landmarks) > 1:
        issues.append("multi")

    lms = result.pose_landmarks[idx]
    P = {i: (lms[i].x * w, lms[i].y * h) for i in range(len(lms))}
    vis_score = {i: (lms[i].visibility or 0.0) for i in range(len(lms))}
    geo["P"] = P
    geo["min_visibility"] = min(vis_score[i] for i in REQUIRED)

    # 1) 품질 검사
    low = [i for i in REQUIRED if vis_score[i] < config.BODY_MIN_VISIBILITY
           or not (0 <= lms[i].x <= 1 and 0 <= lms[i].y <= 1)]   # 사진 밖으로 '추정'된 점도 제외
    if low:
        geo["low_visibility_landmarks"] = low
        issues.append("feet_hidden" if set(low) <= {L_KNEE, R_KNEE, L_ANK, R_ANK} else "body_hidden")
        return issues, None, geo
    tilt = math.degrees(math.atan2(P[L_SH][1] - P[R_SH][1], P[L_SH][0] - P[R_SH][0]))
    tilt = abs((tilt + 90) % 180 - 90)
    geo["tilt"] = tilt
    if tilt > config.BODY_MAX_SHOULDER_TILT_DEG:
        issues.append("tilt")
    sh_w_joint = abs(P[L_SH][0] - P[R_SH][0])
    torso_len = abs((P[L_HIP][1] + P[R_HIP][1]) / 2 - (P[L_SH][1] + P[R_SH][1]) / 2)
    if sh_w_joint < 0.35 * torso_len:
        issues.append("side")
    leg_len = (P[L_ANK][1] + P[R_ANK][1]) / 2 - (P[L_HIP][1] + P[R_HIP][1]) / 2
    if leg_len < 1.2 * torso_len:
        issues.append("not_standing")
        return issues, None, geo

    # 2) 사람 실루엣 마스크 (사람 1명에 해당하는 덩어리만)
    if not result.segmentation_masks:
        issues.append("no_mask")
        return issues, None, geo
    m = (result.segmentation_masks[idx].numpy_view().squeeze() > 0.5).astype(np.uint8)
    n, lab = cv2.connectedComponents(m)
    hip_c = ((P[L_HIP][0] + P[R_HIP][0]) / 2, (P[L_HIP][1] + P[R_HIP][1]) / 2)
    k = lab[int(np.clip(hip_c[1], 0, h - 1)), int(np.clip(hip_c[0], 0, w - 1))]
    if k == 0:
        issues.append("no_mask")
        return issues, None, geo
    mask = lab == k
    geo["mask"] = mask

    ys = np.where(mask.any(axis=1))[0]
    top_y = ys.min()
    foot_y = max(P[L_HEEL][1], P[R_HEEL][1], ys.max())
    geo["top_y"], geo["foot_y"] = top_y, foot_y
    if top_y <= max(2, config.BODY_EDGE_MARGIN * h):
        issues.append("head_cut")
    if foot_y >= h - max(3, config.BODY_EDGE_MARGIN * h):
        issues.append("feet_cut")
    px_height = foot_y - top_y
    cm_per_px = height_cm / px_height
    geo["cm_per_px"] = cm_per_px

    # 3) 폭 측정: 몸 중심선을 지나는 연속 구간의 길이
    sh_y = (P[L_SH][1] + P[R_SH][1]) / 2
    hip_y = hip_c[1]
    torso = hip_y - sh_y

    # 팔이 몸통 옆에 붙어 있으면 허리·골반 폭에 팔까지 들어감 → 팔 영역을 지우고 잰다.
    # (어깨선 바로 아래까지는 지우지 않음: 어깨 폭에는 삼각근이 포함돼야 하므로)
    torso_mask = mask.copy()
    arm = np.zeros(mask.shape, np.uint8)
    thick = max(3, int(0.30 * sh_w_joint))
    for chain in ((L_SH, L_EL, L_WR, L_IDX), (R_SH, R_EL, R_WR, R_IDX)):
        pts = np.array([P[i] for i in chain], np.int32)
        cv2.polylines(arm, [pts], False, 1, thick)
    arm[: int(sh_y + 0.15 * torso)] = 0
    torso_mask[arm.astype(bool)] = False
    geo["torso_mask"] = torso_mask

    def center_x(y):
        t = (y - sh_y) / torso
        return (P[L_SH][0] + P[R_SH][0]) / 2 * (1 - t) + hip_c[0] * t

    def width(y):
        y = int(np.clip(y, 0, h - 1))
        src = mask if y < sh_y + 0.15 * torso else torso_mask
        run = _run_at(src[y], center_x(y))
        return (run[1] - run[0] + 1, run, y) if run else (None, None, y)

    shoulder = width(sh_y + 0.04 * torso)  # 어깨관절보다 살짝 아래(삼각근 바깥선)
    waist = min((width(sh_y + t * torso) for t in np.linspace(*config.BODY_WAIST_SEARCH, 25)),
                key=lambda x: x[0] or 1e9)
    hip = max((width(sh_y + t * torso) for t in np.linspace(*config.BODY_HIP_SEARCH, 25)),
              key=lambda x: x[0] or 0)
    if not all(x[0] for x in (shoulder, waist, hip)):
        issues.append("width_fail")
        return issues, None, geo
    geo["lines"] = {"shoulder": shoulder, "waist": waist, "hip": hip}

    # 팔이 몸통에 붙어 있으면 허리·골반 폭이 부풀려짐 → 손목 위치로 확인
    full_run = _run_at(mask[int(np.clip(hip[2], 0, h - 1))], center_x(hip[2]))
    for wr in (L_WR, R_WR):
        if full_run and vis_score[wr] > 0.5 and full_run[0] - 5 <= P[wr][0] <= full_run[1] + 5 and \
                sh_y + 0.6 * torso <= P[wr][1] <= sh_y + 1.3 * torso:
            issues.append("arms")
            break

    # 샅(가랑이) 높이: 무릎 높이의 다리 사이 빈틈에서 몸 중심선을 따라 위로 올라가다
    # 몸 영역이 처음 나오는 곳 (아래→위로 찾아야 옷 주름·벨트의 작은 구멍에 속지 않음)
    cx_leg = int(np.clip((P[L_KNEE][0] + P[R_KNEE][0]) / 2, 0, w - 1))
    knee_y = int(np.clip(min(P[L_KNEE][1], P[R_KNEE][1]), 0, h - 1))
    crotch_y = None
    if not mask[knee_y, cx_leg]:
        for y in range(knee_y, int(hip_y), -1):
            if mask[y, cx_leg]:
                crotch_y = y
                break
    # 샅높이가 키의 36~55% 를 벗어나면 잘못 찾은 것으로 보고 버림 (Size Korea 20대 남성 평균 약 46%)
    if crotch_y is not None and not (0.36 <= (foot_y - crotch_y) / px_height <= 0.55):
        crotch_y = None
    if crotch_y is None:
        issues.append("legs")
    geo["crotch"] = (cx_leg, crotch_y)

    # 팔 길이: 어깨관절 → 팔꿈치 → 손목 (좌우 평균)
    def arm_len(s_, e_, w_):
        return np.hypot(*np.subtract(P[s_], P[e_])) + np.hypot(*np.subtract(P[e_], P[w_]))
    arm_px = (arm_len(L_SH, L_EL, L_WR) + arm_len(R_SH, R_EL, R_WR)) / 2

    ref = config.SIZE_KOREA_REF_MM
    H_ref = ref["height"]

    def dev(px, ref_mm):  # 키 대비 비율을 Size Korea 평균과 비교한 편차
        return (px / px_height) / (ref_mm / H_ref) - 1

    waist_dev = dev(waist[0], ref["waist_width"])
    hip_dev = dev(hip[0], ref["hip_width"])
    ref_trunk_leg = (ref["shoulder_height"] - ref["crotch_height"]) / ref["crotch_height"]
    f = {
        "shoulder_width_cm": shoulder[0] * cm_per_px,
        "waist_width_cm": waist[0] * cm_per_px,
        "hip_width_cm": hip[0] * cm_per_px,
        "crotch_height_cm": (foot_y - crotch_y) * cm_per_px if crotch_y else None,
        "arm_length_cm": arm_px * cm_per_px,
        # ↓ Size Korea 20대 남성 평균 대비 편차 (+0.05 = 키 대비 5% 큼)
        "shoulder_dev": dev(shoulder[0], ref["bideltoid_width"]),
        "waist_dev": waist_dev,
        "hip_dev": hip_dev,
        "trunk_dev": (waist_dev + hip_dev) / 2,          # 몸통 굵기
        "taper_dev": (waist[0] / hip[0]) / (ref["waist_width"] / ref["hip_width"]) - 1,  # +면 허리가 상대적으로 굵음
        "arm_dev": dev(arm_px, ref["arm_length"]),
        "limb_dev": (((crotch_y - sh_y) / (foot_y - crotch_y)) / ref_trunk_leg - 1  # -면 '긴 지체'(다리가 긴 편)
                     if crotch_y else None),
        "waist_hip_ratio": waist[0] / hip[0],
    }
    if not plausible(f):
        issues.append("implausible")
        return issues, None, geo
    return issues, f, geo


def plausible(f):
    """어깨·몸통 편차가 사람 몸으로 볼 수 있는 범위(config.BODY_PLAUSIBLE_DEV) 안인지."""
    for k, (lo, hi) in config.BODY_PLAUSIBLE_DEV.items():
        v = f.get(k)
        if v is None or not (lo <= v <= hi):
            return False
    return True


def issue_text(code, geo=None, live=False):
    fatal, photo, short = ISSUES[code]
    if code == "tilt" and geo and not live:
        return f"어깨가 {geo['tilt']:.0f}° 기울어 있음 — 똑바로 선 자세 권장"
    return short if live else photo


def analyze_body(image_path, height_cm, annotate=True):
    """사진 파일 1장 분석 (run_test.py 등). 실제 계산은 analyze_body_rgb 가 함."""
    return analyze_body_rgb(load_rgb(image_path), height_cm, annotate=annotate, file=str(image_path))


def analyze_body_rgb(rgb, height_cm, annotate=True, file=None):
    """RGB 배열 1장 분석 — 파일 없이 메모리에서 처리 (AI 서버 server.py 가 사용).
    out["issues"] = 촬영 문제 코드 목록 (ISSUES 의 키), out["quality"] = 촬영 품질 원시값."""
    rgb = np.ascontiguousarray(rgb)
    h, w = rgb.shape[:2]
    result = _get_landmarker().detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
    out = {"file": file, "ok": False, "height_cm": height_cm, "warnings": []}

    issues, f, geo = measure(result, h, w, height_cm)
    out["issues"] = issues
    out["quality"] = {k: round(float(geo[k]), 2) for k in ("min_visibility", "tilt") if k in geo}
    fatal = [c for c in issues if ISSUES[c][0]]
    out["warnings"] = [issue_text(c, geo) for c in issues if not ISSUES[c][0]]
    if fatal:
        out["error"] = issue_text(fatal[0], geo)
        if "low_visibility_landmarks" in geo:
            out["low_visibility_landmarks"] = geo["low_visibility_landmarks"]
        return out, None

    out["features"] = {k: (round(float(v), 3) if v is not None else None) for k, v in f.items()}
    out.update(summarize_body(f))
    out["note"] = "Size Korea 20대 남성 평균과 비교한 임시 판정 — calibrate.py 로 기준값을 보정하세요"
    out["ok"] = True

    vis = draw_measurement(cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR), geo, f, out["body_shape_key"]) if annotate else None
    return out, vis


def draw_measurement(vis, geo, f=None, key=None, label=True):
    """마스크·측정선·관절을 그림 (사진·실시간 공용)."""
    h, w = vis.shape[:2]
    if "mask" in geo:
        mask, torso_mask = geo["mask"], geo.get("torso_mask", geo["mask"])
        overlay = vis.copy()
        overlay[torso_mask] = (overlay[torso_mask] * 0.5 + np.array([255, 180, 0]) * 0.5).astype(np.uint8)
        am = mask & ~torso_mask
        overlay[am] = (overlay[am] * 0.5 + np.array([120, 120, 120]) * 0.5).astype(np.uint8)
        vis = cv2.addWeighted(overlay, 0.6, vis, 0.4, 0)
    th = max(2, int(max(w, h) / 500))
    cmpp = geo.get("cm_per_px")
    for name, col in [("shoulder", (0, 255, 0)), ("waist", (0, 0, 255)), ("hip", (255, 0, 255))]:
        if "lines" not in geo:
            break
        wpx, run, y = geo["lines"][name]
        cv2.line(vis, (run[0], y), (run[1], y), col, th)
        if label and cmpp:
            cv2.putText(vis, f"{name} {wpx * cmpp:.1f}cm", (run[1] + 6, y),
                        cv2.FONT_HERSHEY_SIMPLEX, th * 0.3, col, max(1, th // 2))
    P = geo.get("P", {})
    for i in REQUIRED + [L_WR, R_WR]:
        if i in P:
            cv2.circle(vis, (int(P[i][0]), int(P[i][1])), th * 2, (0, 255, 255), -1)
    cx_leg, crotch_y = geo.get("crotch", (None, None))
    if crotch_y:
        cv2.line(vis, (cx_leg - 15 * th, int(crotch_y)), (cx_leg + 15 * th, int(crotch_y)), (0, 165, 255), th)
    if label and f and key:
        cv2.putText(vis, f"{key}  shoulder{f['shoulder_dev']:+.0%} trunk{f['trunk_dev']:+.0%}",
                    (10, 25 * th), cv2.FONT_HERSHEY_SIMPLEX, th * 0.35, (0, 255, 255), max(1, th // 2))
    return vis
