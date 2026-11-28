"""
판정 기준값 모음.

!! 여기 숫자는 전부 '임시값'입니다 !!
- 기능 확인용 출발점일 뿐, 정답 기준이 아닙니다.
- 팀이 라벨을 붙인 사진으로 calibrate.py 를 돌린 뒤, 그 결과를 보고 이 값을 고치세요.
- 분류 이름(얼굴형/체형 목록)도 팀에서 확정하면 바꾸면 됩니다.
"""

MODEL_DIR = "models"
FACE_MODEL = "face_landmarker.task"
POSE_MODEL = "pose_landmarker_full.task"   # 정확도를 더 원하면 heavy 모델로 교체

# ---------------------------------------------------------------------------
# 촬영 품질 검사
# ---------------------------------------------------------------------------
FACE_MAX_YAW_DEG = 10      # 좌우로 고개 돌린 각도 허용치
FACE_MAX_PITCH_DEG = 15    # 위아래로 숙이거나 든 각도 허용치
FACE_MIN_WIDTH_PX = 200    # 얼굴 폭이 이보다 작으면 해상도 부족 경고
FACE_MAX_JAW_OPEN = 0.15   # 입 벌림 점수(0~1) 허용치 — 입을 벌리면 얼굴이 길게 측정됨
FACE_MAX_SMILE = 0.35      # 웃음 점수(0~1) 허용치 — 웃으면 볼·턱 윤곽이 바뀜

# ---------------------------------------------------------------------------
# 얼굴형 측정 안정화
# ---------------------------------------------------------------------------
FACE_FRONTALIZE = True     # 3D 랜드마크를 정면으로 돌려 세운 뒤 측정 (고개 각도 영향 줄이기)
FACE_TTA = True            # 원본·좌우반전·얼굴 확대본에서 각각 재고 평균 (랜드마크 떨림 줄이기, 4배 느려짐)

# 같은 사람을 다시 찍었을 때 특징값이 흔들리는 정도(표준편차).
# 판정 확률(candidates) 계산에 쓰임. consistency.py 의 '표준편차 평균' 값으로 바꾸세요.
FACE_FEATURE_NOISE = {
    "length_ratio": 0.03,
    "forehead_ratio": 0.02,
    "jaw_ratio": 0.02,
    "chin_ratio": 0.02,
    "jaw_angle_deg": 2.0,
}
FACE_MIN_CONFIDENCE = 0.60  # 1위 유형 확률이 이보다 낮으면 '경계 얼굴'로 표시

# ---------------------------------------------------------------------------
# 실시간 모드 (run_live.py)
# ---------------------------------------------------------------------------
LIVE_ROUNDS = "auto"          # 측정 회차: "auto"(2회 일치하면 끝, 다르면 3회) 또는 숫자
LIVE_TARGET_FRAMES = 6        # 회차당 모을 좋은 프레임 수
LIVE_MIN_INTERVAL_SEC = 0.30  # 모으는 프레임 사이 최소 간격 (연속 프레임은 거의 같은 사진이라)
LIVE_REPOSITION_YAW_DEG = 15  # 회차 사이: 고개를 이 각도 이상 돌리거나 화면을 벗어나면 '자세 다시 잡음'으로 인정
LIVE_REPOSITION_TIMEOUT_SEC = 5  # 회차 사이: 이 시간이 지나도 고개를 안 돌리면 그냥 다음 회차 진행
LIVE_MIN_FACE_FRAC = 0.18     # 얼굴 폭 / 화면 폭 — 이보다 작으면 "가까이"
LIVE_MAX_FACE_FRAC = 0.50     #                   — 이보다 크면 "뒤로" (가까우면 렌즈 왜곡)
LIVE_MAX_CENTER_OFFSET = 0.20 # 화면 중앙에서 벗어난 정도 허용치
LIVE_MIN_BRIGHTNESS = 70      # 얼굴 영역 평균 밝기(0~255)
LIVE_MAX_MOTION = 0.02        # 직전 프레임 대비 코끝 이동량 / 얼굴 폭 — 크면 "움직이지 마세요"

# ---------------------------------------------------------------------------
# 실시간 체형 모드 (run_live_body.py)
# ---------------------------------------------------------------------------
LIVE_BODY_ROUNDS = "auto"          # 측정 회차: "auto"(2회 일치하면 끝, 다르면 3회) 또는 숫자
LIVE_BODY_TARGET_FRAMES = 5        # 회차당 모을 좋은 프레임 수
LIVE_BODY_MIN_INTERVAL_SEC = 0.30  # 모으는 프레임 사이 최소 간격
LIVE_BODY_COUNTDOWN_SEC = 3        # 자세가 맞으면 이 시간 카운트다운 후 측정 시작 (첫 회차)
LIVE_BODY_NEXT_COUNTDOWN_SEC = 1.5 # 2회차부터의 카운트다운
LIVE_BODY_REPOSITION_TIMEOUT_SEC = 6  # 회차 사이: 자리를 안 옮겨도 이 시간 뒤 다음 회차 진행
LIVE_BODY_EDGE_MARGIN = 0.02       # 머리 위·발 밑 최소 여백 (화면 높이 대비)
LIVE_BODY_MIN_HEIGHT_FRAC = 0.60   # 사람 키 / 화면 높이 — 이보다 작으면 "가까이"
LIVE_BODY_MAX_CENTER_OFFSET = 0.15 # 몸 중심이 화면 가운데에서 벗어난 정도 허용치
LIVE_BODY_MAX_TILT_DEG = 8         # 어깨 기울기 허용치 (사진 모드보다 느슨하게)
LIVE_BODY_MAX_MOTION = 0.03        # 직전 프레임 대비 골반 이동량 / 몸통 길이 — 크면 "움직이지 마세요"
LIVE_BODY_REQUIRE_A_POSE = True    # True: 팔이 몸에 붙어 있으면 측정하지 않음
LIVE_BODY_PROCESS_MAX_SIDE = 960   # 분석할 때 프레임 긴 변을 이 크기로 줄임 (속도)

BODY_MIN_VISIBILITY = 0.6  # 어깨·골반·무릎·발목 랜드마크 최소 신뢰도
BODY_MAX_SHOULDER_TILT_DEG = 6
BODY_EDGE_MARGIN = 0.0     # 머리 위·발 밑 최소 여백 (사진 높이 대비). 실시간 모드는 아래 LIVE_BODY_EDGE_MARGIN 사용

# ---------------------------------------------------------------------------
# 얼굴형: 얼굴 높이를 0(이마 위) ~ 1(턱끝)로 봤을 때 폭을 재는 위치
# MediaPipe 얼굴 메시의 맨 위 점은 '헤어라인'이 아니라 이마 윗부분이라
# 실제 얼굴 길이보다 조금 짧게 잡힙니다. (모든 사진에 똑같이 적용되므로 비교에는 문제 없음)
# ---------------------------------------------------------------------------
FACE_T_FOREHEAD = 0.18
# 광대 폭을 재는 높이는 랜드마크(234, 454) 위치로 자동 결정됨
FACE_T_JAW = 0.80
FACE_T_CHIN = 0.92

# ---------------------------------------------------------------------------
# 얼굴형 판정 방식
#   "relative" (기본): 평균 얼굴과 비교해 가장 두드러진 특징으로 판정. 평균에 가까우면 계란형.
#   "rules"          : 아래 FACE_RULES 고정 기준값 (예전 방식 — 계란형이 몰리기 쉬움)
# ---------------------------------------------------------------------------
FACE_CLASSIFIER = "relative"

# 평균 얼굴 기준값 (중앙값, 사람 간 퍼짐 정도).
# !! 임시값 — 테스트 얼굴 7명으로 잡은 값입니다. 팀이 모은 사진으로 fit_population.py 를 돌리면
#    face_population.json 이 생기고, 그 값이 자동으로 우선 적용됩니다.
FACE_POP_MEDIAN = {"length_ratio": 1.21, "forehead_ratio": 0.92, "jaw_ratio": 0.83,
                   "chin_ratio": 0.67, "jaw_angle_deg": 132.5}
FACE_POP_SPREAD = {"length_ratio": 0.06, "forehead_ratio": 0.015, "jaw_ratio": 0.04,
                   "chin_ratio": 0.04, "jaw_angle_deg": 4.5}
# 퍼짐 정도의 하한 — 측정 오차보다 작은 차이를 '특징'으로 과장하지 않게
FACE_Z_MIN_SPREAD = {"length_ratio": 0.03, "forehead_ratio": 0.012, "jaw_ratio": 0.025,
                     "chin_ratio": 0.025, "jaw_angle_deg": 2.5}

# 특징별 신뢰도(0~1): 같은 사람을 다시 쟀을 때 잘 흔들리는 특징은 판정에서 덜 반영.
# 이마 폭은 사람끼리 차이가 작아 측정 오차의 영향이 커서 낮게 둠.
# !! 임시값 — fit_population.py 에 같은 사람 반복 측정 데이터(run_live.py --person)를 넣으면 자동 계산됨
FACE_FEATURE_WEIGHTS = {"length_ratio": 1.0, "forehead_ratio": 0.5, "jaw_ratio": 0.9,
                        "chin_ratio": 0.8, "jaw_angle_deg": 0.8}

# 유형별 '방향': 평균보다 클수록(+) / 작을수록(-) 그 유형에 가까움. 숫자는 중요도.
#   jaw_angle_deg 는 작을수록 각진 턱 → 각진 유형은 음수
FACE_PROTOTYPES = {
    "long":      {"length_ratio": +1.0, "jaw_ratio": -0.3, "jaw_angle_deg": +0.3},            # 길고 턱선 부드러움
    "rectangle": [{"length_ratio": +0.7, "jaw_ratio": +0.8, "jaw_angle_deg": -0.8},           # 길고 턱이 넓고 각짐
                  {"jaw_ratio": +0.8, "jaw_angle_deg": -1.0}],                                 # 길이와 무관하게 턱이 넓고 각짐
                  #  ↑ 목록에 '각진형'이 없어서 각진 얼굴은 직사각형으로 (팀 결정 — 바꾸려면 이 줄 삭제)
    "round":     {"length_ratio": -1.0, "jaw_angle_deg": +0.5, "chin_ratio": +0.4},           # 짧고 둥근 턱
    "heart":     {"forehead_ratio": +0.7, "jaw_ratio": -0.8, "chin_ratio": -0.8},             # 넓은 이마, 좁고 뾰족한 턱
    "diamond":   {"forehead_ratio": -0.8, "jaw_ratio": -0.7, "chin_ratio": -0.3},             # 광대만 넓음
}
# 가장 높은 유형 점수가 이 값보다 작으면 계란형(평균형).
# 키우면 계란형이 늘고, 줄이면 다른 유형이 늘어남. fit_population.py 가 목표 비율에 맞춰 추천해 줌.
FACE_OVAL_RADIUS = 0.8

# (FACE_CLASSIFIER = "rules" 일 때만 사용) 얼굴형 고정 규칙 — 분류 6종은 팀 확정안
# 판정 순서: 직사각형/긴형 → 다이아몬드형 → 하트형 → 둥근형 → 계란형
FACE_RULES = {
    "long_min_length_ratio": 1.30,     # 길이/광대폭 이 이상이면 '긴 얼굴' 그룹
    "rect_min_jaw_ratio": 0.86,        # 턱폭/광대폭 이 이상이거나
    "rect_max_jaw_angle": 132,         #   턱 각도가 이 이하이면(각진 턱) → 직사각형, 아니면 긴형
    "diamond_max_forehead_ratio": 0.88,    # 이마비 이하 + 턱비 이하(광대만 넓음) → 다이아몬드형
    "diamond_max_jaw_ratio": 0.78,
    "heart_min_forehead_minus_jaw": 0.22,  # 이마비 - 턱비 이 이상 + 턱끝이 뾰족 → 하트형
    "heart_max_chin_ratio": 0.66,
    "round_max_length_ratio": 1.10,    # 길이비 이 이하 → 둥근형
}
# ※ 길지 않은데 턱이 각진 얼굴(일반적인 '각진형')은 분류 목록에 없어서 '직사각형'으로 보냄
FACE_LABELS = {
    "diamond": "다이아몬드형",
    "heart": "하트형",
    "oval": "계란형",
    "rectangle": "직사각형",
    "round": "둥근형",
    "long": "긴형",
}

# ---------------------------------------------------------------------------
# 체형: Size Korea 20대 남성 Pattern 기준 (표준체형 · 작은 역삼각체형 · 큰 사각체형 · 사각체형)
#
# Size Korea 는 '키에 대한 비율'을 평균(표준체형)과 비교해서 Pattern 을 나눕니다.
# 여기서는 사진에서 잴 수 있는 항목만 골라 같은 방식으로 비교합니다.
# 기준값 출처: sizekorea.kr > 한국인의 체형 분류 > 20대 남자 (세 Pattern 치수의 평균, mm)
# 다른 연령대를 쓰려면 사이트에서 해당 연령 값을 찾아 바꾸세요.
# ---------------------------------------------------------------------------
SIZE_KOREA_REF_MM = {
    "height": 1732,               # (102) 키
    "bideltoid_width": 470,       # (318) 위팔사이너비 — 사진 어깨선(삼각근 바깥)과 같은 위치
    "waist_width": 271,           # (118) 허리너비
    "hip_width": 330,             # (120) 엉덩이너비
    "shoulder_height": 1399,      # (105) 어깨높이
    "crotch_height": 794,         # (110) 샅높이
    "arm_length": 581,            # (236) 팔길이
}

BODY_WAIST_SEARCH = (0.45, 0.90)  # 어깨선(0)~골반관절선(1) 사이에서 가장 좁은 곳 = 허리
BODY_HIP_SEARCH = (0.85, 1.15)    # 골반관절선 주변에서 가장 넓은 곳 = 엉덩이

# 편차 = (내 값/키) ÷ (평균 값/키) - 1   예) +0.06 = 평균보다 6% 큼

# 사람 몸으로 볼 수 있는 편차 범위. 벗어나면 잘못 잰 프레임으로 보고 버림
# (팔을 옆으로 벌려 팔까지 어깨로 잡히거나, 배경이 몸으로 잡힌 경우).
# 어깨 -0.30~+0.50 ≈ 키 173cm 기준 어깨너비 33~70cm / 몸통 -0.30~+0.70
# 팀 데이터 실제 범위: 어깨 -0.06~+0.37, 몸통 +0.05~+0.49 (2026-11-26, 14명)
BODY_PLAUSIBLE_DEV = {
    "shoulder_dev": (-0.30, 0.50),
    "trunk_dev": (-0.30, 0.70),
}

# ---------------------------------------------------------------------------
# 체형 판정 방식
#   "relative" (기본): 팀 데이터의 평균 체형과 비교한 '상대 위치'로 판정.
#                      사진 측정의 공통 치우침(옷 두께, 머리카락·신발 등)이 기준점에서 상쇄됨.
#   "rules"          : Size Korea 평균 대비 절대 편차 (아래 BODY_RULES) — 옷 때문에 몸통이 굵게 재지면
#                      작은 역삼각체형·표준체형이 거의 안 나오는 문제가 있었음
# ---------------------------------------------------------------------------
BODY_CLASSIFIER = "relative"
BODY_RELATIVE_WITHOUT_FIT = False   # False: 팀 기준(body_population.json)이 없으면 예전 규칙으로 판정

# 평균 체형의 위치(편차 값)와 사람 간 퍼짐.
# !! 임시값 — 'Size Korea 평균 = 우리 평균'이라고 가정한 값. 팀 측정값으로 fit_body_population.py 를 돌리면
#    body_population.json 이 생기고 자동으로 우선 적용됩니다.
BODY_POP_MEDIAN = {"shoulder_dev": 0.0, "trunk_dev": 0.0, "upper_taper": 0.0}
BODY_POP_SPREAD = {"shoulder_dev": 0.05, "trunk_dev": 0.06, "upper_taper": 0.055}
# upper_taper = shoulder_dev - trunk_dev  (몸통 굵기에 비해 어깨가 얼마나 넓은지)

# 판정 기준 (평균 대비 '표준편차 몇 배'인지). 아래 기본값은 측정값이 평균 주변에 고르게 퍼져 있을 때
#   표준체형 20% + 나머지를 Size Korea 20대 남성 구성비(작은 역삼각 30.8 : 큰 사각 33.2 : 사각 35.9)로
#   나누도록 계산한 값입니다. fit_body_population.py 가 팀 데이터에 맞춰 다시 계산해 줍니다.
BODY_REL_RULES = {
    "standard_radius": 0.665,     # 어깨·몸통 모두 평균에 가까우면(이 거리 이내) → 표준체형
    "large_rect_min_trunk_z": 0.613,   # 몸통 굵기가 평균보다 이만큼 이상 굵으면 → 큰 사각체형
    "small_inv_min_taper_z": 0.664,    # 몸통 대비 어깨가 이만큼 이상 넓으면 → 작은 역삼각체형
}                                       # 나머지 → 사각체형

# (BODY_CLASSIFIER = "rules" 일 때만 사용)
BODY_RULES = {
    "standard_tol": 0.05,              # 어깨·몸통 편차가 모두 ±5% 이내 → 표준체형
    "large_rect_min_trunk": 0.08,      # 몸통 굵기 편차 +8% 이상 → 큰 사각체형
    "small_inv_max_trunk": 0.00,       # 몸통이 평균 이하로 가늘고
    "small_inv_min_shoulder_gap": 0.06,  #  (어깨 편차 - 몸통 편차) 가 +6% 이상 → 작은 역삼각체형
}                                       # 나머지 → 사각체형 (몸통 굵기가 고르고 어깨 보통)
# 체형 측정 오차 (같은 사람을 다시 쟀을 때 1회차 값이 흔들리는 정도, 표준편차).
# 판정 확률(확신도)을 계산할 때 이만큼 값을 흔들어 봅니다. fit_body_population.py 가 팀 데이터로 다시 계산해
# body_population.json 에 넣으면 그 값이 우선 적용됩니다. 아래는 2026-11-26 팀 데이터(15명) 값.
BODY_FEATURE_NOISE = {"shoulder_dev": 0.0437, "trunk_dev": 0.0436}
BODY_MIN_CONFIDENCE = 0.60   # 1위 체형 확률이 이보다 낮으면 '두 체형의 경계'로 표시 (얼굴과 같은 기준)

BODY_LABELS = {
    "standard": "표준체형",
    "small_inv_triangle": "작은 역삼각체형",
    "large_rectangle": "큰 사각체형",
    "rectangle": "사각체형",
}
