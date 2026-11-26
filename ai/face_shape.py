"""
얼굴형 분석 (MediaPipe Face Landmarker, 무료 · 내 컴퓨터에서만 실행)

흐름
 1) 사진 → 랜드마크 478개(3D) + 표정 점수 + 고개 각도
 2) 촬영 품질 검사 (고개 각도, 입 벌림·웃음, 얼굴 크기)
 3) 3D 랜드마크를 '정면을 보는 얼굴'로 돌려 세움 (고개가 조금 돌아가도 비율이 덜 흔들리게)
 4) 원본 + 좌우반전 + 얼굴 확대본에서 각각 재고 평균 (랜드마크 떨림 줄이기)
 5) 얼굴 윤곽선 기준 폭/길이 비율 계산
 6) 판정 + '측정 오차를 감안했을 때 각 유형일 확률'을 함께 출력 (경계에 걸린 얼굴 표시)
"""
import math

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

import config
from common import angle_at, load_rgb, model_path

# MediaPipe 얼굴 메시의 얼굴 윤곽선(face oval) 점 번호. 순서대로 한 바퀴를 돈다.
FACE_OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379,
             378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
             162, 21, 54, 103, 67, 109]
TOP, CHIN = 10, 152
EYE_OUTER_A, EYE_OUTER_B = 33, 263
CHEEK_A, CHEEK_B = 234, 454
FEATURE_KEYS = ["length_ratio", "forehead_ratio", "jaw_ratio", "chin_ratio", "jaw_angle_deg"]

_landmarker = None


def _get_landmarker():
    global _landmarker
    if _landmarker is None:
        opts = vision.FaceLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_path=model_path(config.FACE_MODEL)),
            running_mode=vision.RunningMode.IMAGE,
            num_faces=2,  # 2명 이상 찍혔는지 확인하려고 2로 둠
            output_face_blendshapes=True,            # 표정(입 벌림·웃음) 확인용
            output_facial_transformation_matrixes=True,  # 고개 각도 확인용
        )
        _landmarker = vision.FaceLandmarker.create_from_options(opts)
    return _landmarker


def _detect(rgb):
    return _get_landmarker().detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb)))


def _head_pose(matrix):
    """4x4 변환 행렬 → (yaw, pitch, roll) 도 단위. 품질 경고용 대략값."""
    r = np.array(matrix)[:3, :3]
    yaw = math.degrees(math.atan2(-r[2, 0], math.hypot(r[0, 0], r[1, 0])))
    pitch = math.degrees(math.atan2(r[2, 1], r[2, 2]))
    roll = math.degrees(math.atan2(r[1, 0], r[0, 0]))
    return yaw, pitch, roll


def _width_at(poly, y):
    """다각형(윤곽선)을 수평선 y 로 잘랐을 때의 (왼쪽 x, 오른쪽 x)."""
    xs = []
    n = len(poly)
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if (y1 - y) * (y2 - y) <= 0 and y1 != y2:
            xs.append(x1 + (y - y1) * (x2 - x1) / (y2 - y1))
    if len(xs) < 2:
        return None
    return min(xs), max(xs)


def _frontalize(p3):
    """3D 랜드마크를 얼굴 기준 좌표계로 변환 → 정면에서 본 2D 좌표.
    x축: 왼쪽 얼굴 옆선 → 오른쪽 옆선(+눈꼬리), y축: 이마 위 → 턱끝, z축: 얼굴 앞쪽."""
    ex = (p3[CHEEK_B] - p3[CHEEK_A]) + (p3[EYE_OUTER_B] - p3[EYE_OUTER_A])
    ex /= np.linalg.norm(ex)
    ey = p3[CHIN] - p3[TOP]
    ey -= ey.dot(ex) * ex
    ey /= np.linalg.norm(ey)
    ez = np.cross(ex, ey)
    local = (p3 - p3.mean(axis=0)) @ np.stack([ex, ey, ez]).T
    return local[:, :2]


def _features_from_2d(p):
    """정면화된 2D 점 → 비율 특징. (실패하면 None)"""
    poly = [tuple(p[i]) for i in FACE_OVAL]
    top_y, chin_y = p[TOP][1], p[CHIN][1]
    H = chin_y - top_y
    if H <= 0:
        return None, None

    def row(t):
        return _width_at(poly, top_y + t * H)

    cheek_t = float(((p[CHEEK_A][1] + p[CHEEK_B][1]) / 2 - top_y) / H)
    fore, cheek = row(config.FACE_T_FOREHEAD), row(cheek_t)
    jaw, chin = row(config.FACE_T_JAW), row(config.FACE_T_CHIN)
    if not all([fore, jaw, chin, cheek]):
        return None, None
    cheek_w = cheek[1] - cheek[0]
    yj, yc = top_y + config.FACE_T_JAW * H, top_y + cheek_t * H
    left = angle_at((jaw[0], yj), (cheek[0], yc), p[CHIN])
    right = angle_at((jaw[1], yj), (cheek[1], yc), p[CHIN])
    f = {
        "length_ratio": H / cheek_w,                       # 얼굴 길이 / 광대 폭
        "forehead_ratio": (fore[1] - fore[0]) / cheek_w,   # 이마 폭 / 광대 폭
        "jaw_ratio": (jaw[1] - jaw[0]) / cheek_w,          # 턱 폭 / 광대 폭
        "chin_ratio": (chin[1] - chin[0]) / (jaw[1] - jaw[0]),  # 턱끝 폭 / 턱 폭 (작을수록 뾰족)
        "jaw_angle_deg": (left + right) / 2,               # 작을수록 각진 턱
    }
    geo = {"poly": poly, "top_y": top_y, "H": H, "fore": fore, "cheek": cheek, "jaw": jaw,
           "chin": chin, "cheek_t": cheek_t}
    return f, geo


def features_from_landmarks(lm, w, h):
    """랜드마크 1세트 → (특징 dict 또는 None, 정면화 2D 점). 사진·실시간 공용."""
    if config.FACE_FRONTALIZE:
        p3 = np.array([(q.x * w, q.y * h, q.z * w) for q in lm])
        p2 = _frontalize(p3)
    else:  # 예전 방식: 눈꼬리 기준 기울기만 보정
        pts = np.array([(q.x * w, q.y * h) for q in lm])
        a, b = pts[EYE_OUTER_A], pts[EYE_OUTER_B]
        ang = math.atan2(b[1] - a[1], b[0] - a[0])
        rot = np.array([[math.cos(-ang), -math.sin(-ang)], [math.sin(-ang), math.cos(-ang)]])
        p2 = (pts - pts.mean(axis=0)) @ rot.T
    f, _ = _features_from_2d(p2)
    return f, p2


def expression_scores(res):
    """표정 점수 (입 벌림, 웃음). 없으면 None."""
    if not res.face_blendshapes:
        return None
    bs = {c.category_name: c.score for c in res.face_blendshapes[0]}
    return {"jaw_open": bs.get("jawOpen", 0.0),
            "smile": (bs.get("mouthSmileLeft", 0.0) + bs.get("mouthSmileRight", 0.0)) / 2}


def head_pose(res):
    if not res.facial_transformation_matrixes:
        return None
    return _head_pose(res.facial_transformation_matrixes[0])


def _measure(rgb):
    """한 장(변형본 포함)에서 특징 측정."""
    res = _detect(rgb)
    if not res.face_landmarks:
        return None, res, None
    h, w = rgb.shape[:2]
    f, p2 = features_from_landmarks(res.face_landmarks[0], w, h)
    return f, res, p2


def _face_crop(rgb, lm):
    """얼굴 주변을 잘라 얼굴 폭이 약 500px 이 되게 키운 이미지 (작은 얼굴 떨림 줄이기)."""
    h, w = rgb.shape[:2]
    xs = np.array([q.x for q in lm]) * w
    ys = np.array([q.y for q in lm]) * h
    fw = xs.max() - xs.min()
    cx, cy = xs.mean(), ys.mean()
    half = fw * 1.1
    x0, x1 = int(max(0, cx - half)), int(min(w, cx + half))
    y0, y1 = int(max(0, cy - half * 1.25)), int(min(h, cy + half * 1.25))
    crop = rgb[y0:y1, x0:x1]
    if crop.size == 0:
        return None
    s = 500 / max(fw, 1)
    return cv2.resize(crop, None, fx=s, fy=s, interpolation=cv2.INTER_CUBIC if s > 1 else cv2.INTER_AREA)


def classify(f):
    """판정 방식 선택: config.FACE_CLASSIFIER = "relative"(평균 대비, 기본) 또는 "rules"(고정 기준값)."""
    if config.FACE_CLASSIFIER == "rules":
        return classify_rules(f)
    return classify_relative(f)


def classify_rules(f):
    """(예전 방식) 고정 기준값 규칙. 어느 규칙에도 안 걸리면 계란형 → 계란형이 몰리기 쉬움."""
    r = config.FACE_RULES
    angular = f["jaw_ratio"] >= r["rect_min_jaw_ratio"] or f["jaw_angle_deg"] <= r["rect_max_jaw_angle"]
    if f["length_ratio"] >= r["long_min_length_ratio"]:
        return "rectangle" if angular else "long"
    if f["jaw_ratio"] >= r["rect_min_jaw_ratio"] and f["jaw_angle_deg"] <= r["rect_max_jaw_angle"]:
        return "rectangle"   # 각진 턱 (목록에 '각진형'이 없어서 직사각형으로)
    if f["forehead_ratio"] <= r["diamond_max_forehead_ratio"] and f["jaw_ratio"] <= r["diamond_max_jaw_ratio"]:
        return "diamond"
    if (f["forehead_ratio"] - f["jaw_ratio"] >= r["heart_min_forehead_minus_jaw"]
            and f["chin_ratio"] <= r["heart_max_chin_ratio"]):
        return "heart"
    if f["length_ratio"] <= r["round_max_length_ratio"]:
        return "round"
    return "oval"


def z_scores(f):
    """특징값 → 평균 대비 몇 '표준편차' 떨어져 있는지 (+면 평균보다 큼)."""
    pop = population()
    return {k: (f[k] - pop["median"][k]) / max(pop["spread"][k], config.FACE_Z_MIN_SPREAD[k])
            for k in FEATURE_KEYS}


def type_scores(f):
    """유형별 점수: 평균 대비 특징(z)이 그 유형의 방향(config.FACE_PROTOTYPES)과 얼마나 일치하는지."""
    z = z_scores(f)
    wts = population()["weights"]   # 특징별 신뢰도 — 측정이 잘 흔들리는 특징은 덜 반영
    out = {}
    for t, protos in config.FACE_PROTOTYPES.items():
        if isinstance(protos, dict):   # 한 유형에 방향이 여러 개일 수 있음 → 가장 잘 맞는 것
            protos = [protos]
        best = -1e9
        for proto in protos:
            p = {k: v * wts.get(k, 1.0) for k, v in proto.items()}
            norm = math.sqrt(sum(v * v for v in p.values())) or 1.0
            best = max(best, sum(z[k] * v for k, v in p.items()) / norm)
        out[t] = best
    return out


def classify_relative(f):
    """(기본) 평균 얼굴과 비교해 가장 두드러진 특징의 유형으로 판정.
    어느 방향으로도 두드러지지 않으면(점수 < FACE_OVAL_RADIUS) 계란형 = '균형 잡힌 평균형'."""
    scores = type_scores(f)
    best = max(scores, key=scores.get)
    return best if scores[best] >= config.FACE_OVAL_RADIUS else "oval"


_REASON = {
    "length_ratio": ("얼굴이 긴 편", "얼굴이 짧은 편"),
    "forehead_ratio": ("이마가 넓은 편", "이마가 좁은 편"),
    "jaw_ratio": ("턱이 넓은 편", "턱이 좁은 편"),
    "chin_ratio": ("턱끝이 넓은 편", "턱끝이 뾰족한 편"),
    "jaw_angle_deg": ("턱선이 부드러운 편", "턱선이 각진 편"),
}


def describe_vs_average(z, th=0.8):
    """평균 대비 두드러진 특징을 문장으로 (앱 결과 화면 문구로 활용 가능)."""
    items = sorted(((abs(v), k, v) for k, v in z.items() if abs(v) >= th), reverse=True)[:3]
    if not items:
        return "전체적으로 평균에 가까운 균형형"
    return ", ".join(_REASON[k][0 if v > 0 else 1] for _, k, v in items)


_POP = None


def population():
    """평균 얼굴 기준값. fit_population.py 가 만든 face_population.json 이 있으면 그걸 우선 사용."""
    global _POP
    if _POP is None:
        import json
        import os
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "face_population.json")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fp:
                data = json.load(fp)
            _POP = {"median": data["median"], "spread": data["spread"], "source": path,
                    "weights": data.get("weights") or dict(config.FACE_FEATURE_WEIGHTS)}
            if "oval_radius" in data:
                config.FACE_OVAL_RADIUS = data["oval_radius"]
            if data.get("noise"):   # 팀 데이터로 잰 '회차 간 흔들림' → 판정 확률 계산에 사용
                config.FACE_FEATURE_NOISE = data["noise"]
        else:
            _POP = {"median": config.FACE_POP_MEDIAN, "spread": config.FACE_POP_SPREAD,
                    "weights": dict(config.FACE_FEATURE_WEIGHTS), "source": "config.py (임시값)"}
    return _POP


def noise_scale(n_photos):
    """사진 n장의 중앙값을 쓰면 흔들림이 줄어드는 비율.
    중앙값의 표준오차 ≈ 1.2533 × σ / √n (n=1 이면 그대로)."""
    return 1.0 if n_photos <= 1 else 1.2533 / math.sqrt(n_photos)


def probabilities(f, n=400, seed=0, n_photos=1):
    """측정 오차(config.FACE_FEATURE_NOISE)만큼 값을 흔들어 n번 판정 → 유형별 확률.
    확률이 한 유형에 몰리지 않으면 '경계에 걸린 얼굴'이라는 뜻.
    n_photos: 몇 장의 중앙값인지 (여러 장일수록 흔들림을 작게 잡음)."""
    rng = np.random.default_rng(seed)
    scale = noise_scale(n_photos)
    noise = {k: v * scale for k, v in config.FACE_FEATURE_NOISE.items()}
    counts = {}
    for _ in range(n):
        g = {k: f[k] + rng.normal(0, noise.get(k, 0)) for k in FEATURE_KEYS}
        key = classify(g)
        counts[key] = counts.get(key, 0) + 1
    return sorted(((k, v / n) for k, v in counts.items()), key=lambda x: -x[1])


def summarize(f, n_photos=1):
    """특징값 → 판정 결과 dict (여러 장 종합에도 재사용)."""
    probs = probabilities(f, n_photos=n_photos)
    key = probs[0][0]
    out = {
        "face_shape_key": key,
        "face_shape": config.FACE_LABELS[key],
        "confidence": round(probs[0][1], 2),
        "candidates": [{"key": k, "face_shape": config.FACE_LABELS[k], "prob": round(p, 2)} for k, p in probs[:3]],
    }
    if config.FACE_CLASSIFIER != "rules":
        z = z_scores(f)
        out["vs_average"] = {k: round(v, 2) for k, v in z.items()}
        out["reason"] = describe_vs_average(z)
    if probs[0][1] < config.FACE_MIN_CONFIDENCE and len(probs) > 1:
        a = config.FACE_LABELS[key]
        josa = "과" if (0xAC00 <= ord(a[-1]) <= 0xD7A3 and (ord(a[-1]) - 0xAC00) % 28) else "와"   # 받침 있으면 '과'
        out["borderline"] = (f"{a}{josa} {config.FACE_LABELS[probs[1][0]]}의 경계에 있는 얼굴 "
                             f"— 사진 여러 장을 종합하거나 사용자가 직접 고르게 하는 것을 권장")
    return out


def analyze_face(image_path, annotate=True):
    """사진 파일 1장 분석 (run_test.py 등). 실제 계산은 analyze_face_rgb 가 함."""
    return analyze_face_rgb(load_rgb(image_path), annotate=annotate, file=str(image_path))


def analyze_face_rgb(rgb, annotate=True, file=None):
    """RGB 배열 1장 분석 — 파일 없이 메모리에서 처리 (AI 서버 server.py 가 사용).
    out["warning_codes"] 에 api-spec.md 부록 A.5 경고 코드를 같이 넣음."""
    h, w = rgb.shape[:2]
    out = {"file": file, "ok": False, "usable": True, "warnings": [], "warning_codes": []}

    f0, res, _ = _measure(rgb)
    if not res.face_landmarks:
        out["error"] = "얼굴을 찾지 못했습니다. 정면 얼굴이 잘 보이는 사진인지 확인하세요."
        out["warning_codes"].append("FACE_NOT_DETECTED")
        return out, None
    if f0 is None:
        out["error"] = "얼굴 윤곽선을 계산하지 못했습니다 (얼굴 일부가 가려졌을 수 있음)."
        out["warning_codes"].append("FACE_NOT_DETECTED")
        return out, None
    if len(res.face_landmarks) > 1:
        out["warnings"].append("얼굴이 2개 이상 감지됨 — 첫 번째 얼굴만 분석")
        out["warning_codes"].append("MULTIPLE_FACES")
    lm = res.face_landmarks[0]

    def bad(msg, code):  # 판정을 믿기 어려운 사진
        out["warnings"].append(msg)
        out["warning_codes"].append(code)
        out["usable"] = False

    # 1) 품질 검사: 고개 각도
    if res.facial_transformation_matrixes:
        yaw, pitch, roll = _head_pose(res.facial_transformation_matrixes[0])
        out["head_pose_deg"] = {"yaw": round(yaw, 1), "pitch": round(pitch, 1), "roll": round(roll, 1)}
        if abs(yaw) > config.FACE_MAX_YAW_DEG:
            bad(f"고개가 옆으로 {yaw:.0f}° 돌아가 있음 — 정면으로 다시 촬영 권장", "FACE_YAW_EXCEEDED")
        if abs(pitch) > config.FACE_MAX_PITCH_DEG:
            bad(f"고개가 위/아래로 {pitch:.0f}° 기울어 있음 — 카메라를 눈높이에 두고 촬영 권장", "FACE_PITCH_EXCEEDED")

    # 2) 품질 검사: 표정 (입을 벌리면 얼굴이 길어지고, 웃으면 턱·볼 윤곽이 바뀜)
    if res.face_blendshapes:
        bs = {c.category_name: c.score for c in res.face_blendshapes[0]}
        jaw_open = bs.get("jawOpen", 0)
        smile = (bs.get("mouthSmileLeft", 0) + bs.get("mouthSmileRight", 0)) / 2
        out["expression"] = {"jaw_open": round(jaw_open, 2), "smile": round(smile, 2)}
        if jaw_open > config.FACE_MAX_JAW_OPEN:
            bad("입이 벌어져 있음 — 입을 다문 무표정으로 다시 촬영 권장", "MOUTH_OPEN")
        if smile > config.FACE_MAX_SMILE:
            bad("웃는 표정 — 무표정으로 다시 촬영 권장", "SMILING")

    # 3) 여러 변형본에서 재고 평균 (원본, 좌우반전, 얼굴 확대본, 확대본 반전)
    feats = [f0]
    variants = [rgb[:, ::-1]]
    crop = _face_crop(rgb, lm)
    if crop is not None:
        variants += [crop, crop[:, ::-1]]
        face_px = max(q.x for q in lm) * w - min(q.x for q in lm) * w
        out["face_width_px"] = round(face_px)
        if face_px < config.FACE_MIN_WIDTH_PX:
            out["warnings"].append(f"얼굴 폭이 {face_px:.0f}px 로 작음 — 확대해서 쟀지만 더 가까이서 찍는 게 좋음")
            out["warning_codes"].append("FACE_TOO_SMALL")
    if config.FACE_TTA:
        for v in variants:
            fv, _, _ = _measure(v)
            if fv:
                feats.append(fv)
    f = {k: float(np.mean([x[k] for x in feats])) for k in FEATURE_KEYS}
    out["features"] = {k: round(v, 3) for k, v in f.items()}
    out["n_measurements"] = len(feats)

    out.update(summarize(f))
    if not out["usable"]:
        out["note"] = "촬영 조건 때문에 이 사진의 판정은 신뢰도가 낮음 (여러 장 종합 시 제외됨)"
    else:
        out["note"] = ("평균 얼굴 기준이 임시값 — fit_population.py 로 팀 데이터에 맞추세요"
                       if config.FACE_CLASSIFIER != "rules" and "임시" in population()["source"]
                       else "팀 데이터 평균 기준 판정" if config.FACE_CLASSIFIER != "rules"
                       else "고정 규칙(rules) 판정 — calibrate.py 로 기준값을 보정하세요")
    out["ok"] = True

    vis = None
    if annotate:
        vis = _draw(rgb, lm, out)
    return out, vis


def _draw(rgb, lm, out):
    """원본 사진 위에 얼굴 윤곽선과 측정 위치를 표시 (기울기만 보정한 2D 기준, 확인용)."""
    h, w = rgb.shape[:2]
    vis = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    pts = np.array([(q.x * w, q.y * h) for q in lm])
    a, b = pts[EYE_OUTER_A], pts[EYE_OUTER_B]
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    c = pts.mean(axis=0)
    rot = np.array([[math.cos(-ang), -math.sin(-ang)], [math.sin(-ang), math.cos(-ang)]])
    inv = rot.T
    p = (pts - c) @ rot.T
    _, geo = _features_from_2d(p)

    def back(x, y):
        q = np.array([x, y]) @ inv.T + c
        return int(q[0]), int(q[1])

    th = max(2, int(w / 400))
    cv2.polylines(vis, [np.array([pts[i] for i in FACE_OVAL], np.int32)], True, (0, 255, 255), th)
    if geo:
        t_y = geo["top_y"]
        H = geo["H"]
        for (xl, xr), y, col, name in [
            (geo["fore"], t_y + config.FACE_T_FOREHEAD * H, (255, 128, 0), "forehead"),
            (geo["cheek"], t_y + geo["cheek_t"] * H, (0, 200, 0), "cheek"),
            (geo["jaw"], t_y + config.FACE_T_JAW * H, (0, 0, 255), "jaw"),
            (geo["chin"], t_y + config.FACE_T_CHIN * H, (255, 0, 255), "chin"),
        ]:
            cv2.line(vis, back(xl, y), back(xr, y), col, th)
            cv2.putText(vis, name, back(xr + 8, y), cv2.FONT_HERSHEY_SIMPLEX, th * 0.35, col, th)
    f = out["features"]
    cands = " / ".join(f"{x['key']} {x['prob']:.0%}" for x in out["candidates"])
    cv2.putText(vis, cands, (10, 30 * th), cv2.FONT_HERSHEY_SIMPLEX, th * 0.4, (0, 255, 255), th)
    cv2.putText(vis, f"L/W={f['length_ratio']:.2f} jaw={f['jaw_ratio']:.2f} angle={f['jaw_angle_deg']:.0f}"
                + ("  [RETAKE]" if not out["usable"] else ""),
                (10, 60 * th), cv2.FONT_HERSHEY_SIMPLEX, th * 0.4, (0, 255, 255), th)
    return vis


def analyze_faces_combined(paths):
    """같은 사람의 사진 여러 장 → 사진별 결과 + 쓸 만한 사진들의 특징 '중앙값'으로 최종 판정."""
    per = []
    for pth in paths:
        r, _ = analyze_face(pth, annotate=False)
        per.append(r)
    good = [r for r in per if r.get("ok") and r.get("usable")]
    if not good:
        good = [r for r in per if r.get("ok")]  # 전부 품질 미달이면 있는 것으로라도
    combined = {"n_photos": len(paths), "n_used": len(good)}
    if good:
        f = {k: float(np.median([r["features"][k] for r in good])) for k in FEATURE_KEYS}
        combined["features"] = {k: round(v, 3) for k, v in f.items()}
        combined.update(summarize(f, n_photos=len(good)))
    return combined, per
