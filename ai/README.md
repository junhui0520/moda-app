# Moda AI 서버 (얼굴형 · 체형 분석)

MediaPipe로 얼굴형 6종·체형 4종을 판정하는 FastAPI 서버입니다.
분석 API 는 앱이 직접 부르지 않고 **백엔드(`ExternalAiAnalysisClient`)만** 호출합니다.
연동 규칙 원본은 `docs/api-spec.md` 부록 A입니다.

분석 방식은 두 가지입니다.

```
[실시간 · 기본]  앱 WebView ── AI 서버의 측정 페이지 /live/live.html (휴대폰 안에서 실시간 측정, 2회차)
                    │ 측정값(숫자)만
                    ▼
                 백엔드 ──(JSON)──> AI 서버 /v1/live/face · /v1/live/body  → 판정

[사진 · 대체]    앱 ──(사진)──> 백엔드 ──(multipart)──> AI 서버 /v1/analyze/face · /v1/analyze/body
```

실시간 방식 연동(백엔드·앱 담당자용)은 **`LIVE_INTEGRATION.md`** 에 정리했습니다.

## 1. 처음 한 번 설정 (Windows 기준)

`ai` 폴더에서:

```bash
python -m venv .venv
.venv\Scripts\activate          # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
python download_models.py        # MediaPipe 모델 2개 + 측정 페이지용 웹 파일 (git 에는 안 올라감)
```

## 2. 서버 실행

```bash
uvicorn server:app --host 0.0.0.0 --port 8000
```

- 상태 확인: 브라우저에서 <http://localhost:8000/health>
- **실시간 측정 페이지**: <http://localhost:8000/live/live.html?mode=face> · <http://localhost:8000/live/live.html?mode=body&height=175>
  (PC 웹캠으로 측정 → 끝나면 판정 결과 표시. 휴대폰에서 열려면 https 주소 필요 — `LIVE_INTEGRATION.md` 4장)
- 직접 눌러 보기: <http://localhost:8000/docs> (사진 올려서 테스트 가능)
- 응답이 백엔드 규칙에 맞는지 자동 확인: `python tests/test_api_contract.py photos` (사진 폴더 경로)
- 휴대폰 측정 계산이 Python 과 같은지 확인: `python tests/test_live_parity.py photos/a.jpg 영상.mp4` (Node.js 필요)

## 3. 백엔드와 연결

백엔드는 기본이 Mock 모드입니다. 아래 환경변수를 주고 백엔드를 실행하면 이 서버를 호출합니다.

```bash
# backend 폴더, PowerShell
$env:MODA_AI_MODE="external"; $env:MODA_AI_BASE_URL="http://localhost:8000"; ./gradlew bootRun
```

배포 환경(Railway 등)에서는 백엔드 서비스 변수에 `MODA_AI_MODE=external`,
`MODA_AI_BASE_URL=<AI 서버 주소>` 를 넣으면 됩니다.

끝에서 끝까지 확인 (서버 둘 다 켠 상태):

```bash
BASE=http://localhost:8080/api/v1
# ① 세션 생성 → 응답의 data.sessionId 를 SID 에 넣기
curl -s -X POST $BASE/sessions/guest -H "Content-Type: application/json" -d '{"photoConsent":true,"deviceLabel":"test"}'
SID=<sessionId>
# ② 기본 정보 (키가 체형 분석에 쓰임)
curl -s -X POST $BASE/sessions/$SID/profile-input -H "Content-Type: application/json" \
  -d '{"age":23,"heightCm":175,"weightKg":68,"hairThickness":"MEDIUM","hairCurl":"STRAIGHT","hairDensity":"MEDIUM"}'
# ③ 얼굴 · ④ 전신 분석 — external 모드라 Mock 헤더 없이 실제 AI 결과가 나옴
curl -s -X POST $BASE/sessions/$SID/analyze/face -F "image=@./face.jpg;type=image/jpeg"
curl -s -X POST $BASE/sessions/$SID/analyze/body -F "image=@./body.jpg;type=image/jpeg"
# ⑥ 상세 (확률·측정값·Size Korea 비교·reason 확인)
curl -s $BASE/sessions/$SID/analysis-detail
```

AI 서버 터미널에 `face request_id=... usable=True shape=...` 로그가 찍히면 연결된 것입니다.
백엔드가 `502 AI_BAD_RESPONSE` 를 주면 응답 형식 문제, `503` 이면 AI 서버 주소·실행 여부를 확인하세요.

## 4. 배포 (Docker · Railway)

```bash
docker build -t moda-ai ./ai
docker run -p 8000:8000 moda-ai
```

Railway: 새 서비스 → 이 저장소 → **Root Directory = `ai`** → Dockerfile 자동 인식.
헬스체크 경로는 `/health`. 모델은 이미지 빌드 때 내려받습니다.

## 5. 응답 형식 요약 (부록 A)

| 항목 | 얼굴 `/v1/analyze/face` | 체형 `/v1/analyze/body` |
|---|---|---|
| 요청 | `image`, `request_id`(선택) | `image`, `height_cm`, `request_id`(선택) |
| 판정 | `face_shape`: diamond / heart / oval / rectangle / round / long | `body_type`: standard / small_inv_triangle / large_rectangle / rectangle |
| 확률 | `confidence`, `borderline`(60% 미만), `candidates` 6종 | 보내지 않음 (체형은 확률 계산 없음, 선택 필드) |
| 측정값 | `features` 5종 + `reason` 문장 | `features` 3종 + `compare_to_size_korea` |
| 판정 불가 | HTTP 200 + `usable: false` + `warnings` 코드 | 같음 |

경고 코드 (`warnings`, 백엔드가 문구·심각도를 붙임):

| 상황 | 코드 |
|---|---|
| 얼굴 없음 / 윤곽 계산 실패 | `FACE_NOT_DETECTED` |
| 얼굴 2개 이상 (첫 얼굴로 분석) | `MULTIPLE_FACES` |
| 고개 좌우 > 10° / 상하 > 15° | `FACE_YAW_EXCEEDED` / `FACE_PITCH_EXCEEDED` |
| 입 벌림 / 웃음 | `MOUTH_OPEN` / `SMILING` |
| 얼굴 폭 < 200px (분석은 진행) | `FACE_TOO_SMALL` |
| 사람 없음 / 2명 이상 | `BODY_NOT_DETECTED` / `MULTIPLE_PEOPLE` |
| 어깨·골반·무릎·발목 안 보임, 머리·발 잘림 | `BODY_LOW_VISIBILITY` |
| 어깨 기울기 > 6° (분석은 진행) | `SHOULDER_TILT` |
| 아래는 **백엔드에 문구가 아직 없는 코드** (앱에는 "촬영 상태를 한 번 더 확인해 주세요"로 표시) | |
| 앉은 자세 | `BODY_NOT_STANDING` |
| 사람 영역·폭 측정 실패 | `BODY_MEASURE_FAILED` |
| 팔을 옆으로 벌려 폭이 비정상 | `BODY_POSE_INVALID` |
| 몸이 옆으로 돌아감 (분석은 진행) | `BODY_NOT_FRONTAL` |
| 팔이 몸통에 붙음 (분석은 진행) | `BODY_ARMS_CLOSE` |
| 다리 간격 없음 — 샅높이만 생략 (분석은 진행) | `BODY_LEGS_CLOSED` |
| 사진 파일을 읽을 수 없음 | `IMAGE_UNREADABLE` |

`FACE_BORDERLINE` / `BODY_BORDERLINE` 은 백엔드가 `borderline=true` 를 보고 직접 붙이므로 AI는 보내지 않습니다.
코드 대응표는 `api_format.py` 의 `BODY_WARNING_CODES` 에 있습니다.

## 6. 파일 구성

| 파일 | 역할 |
|---|---|
| `server.py` | FastAPI 서버 (사진 분석 2개 · 실시간 판정 2개 · 측정 페이지) |
| `api_format.py` | 분석 결과 → 백엔드 응답 형식 변환, 경고 코드 대응, `reason` 문장 |
| `live/` | **휴대폰 실시간 측정 페이지** — `live.html`, `live_app.js`(화면·회차 진행), `measure_face.js`·`measure_body.js`(측정 계산, Python 과 같은 값) |
| `live_api.py` | 휴대폰 측정값 → 회차 중앙값 → 판정 (`run_live*.py` 의 종합 부분) |
| `integration/LiveAnalysisView.tsx` | 앱에 복사해 쓰는 WebView 컴포넌트 |
| `LIVE_INTEGRATION.md` | 실시간 방식 백엔드·앱 연동 가이드 |
| `face_shape.py` · `body_shape.py` | 얼굴형·체형 측정과 판정 (서버와 측정 도구가 함께 씀) |
| `config.py` | 모든 기준값 |
| `face_population.json` · `body_population.json` | **팀 판정 기준** (평균값만 들어 있음, git 에 올림) |
| `run_live.py` · `run_live_body.py` | 웹캠 실시간 측정 (팀원 데이터 모으기) |
| `fit_population.py` · `fit_body_population.py` | 측정 데이터로 팀 기준 다시 계산 (`--save`) |
| `run_test.py` · `show_criteria.py` · `consistency.py` · `calibrate.py` · `compare_methods.py` | 사진 테스트·기준 확인·일관성 점검 도구 |
| `TOOLS.md` | 측정 도구 상세 사용법 |
| `tests/test_api_contract.py` | 응답이 백엔드 규칙을 지키는지 확인 (사진·실시간 모두) |
| `tests/test_live_parity.py` | 측정 페이지(JS) 계산이 Python 과 같은지 확인 |

## 7. 팀 기준 업데이트 흐름

1. 실시간 측정으로 데이터 모으기 — `python run_live.py --person 이름`, `python run_live_body.py --person 이름 --height 175`
2. 기준 다시 계산 — `python fit_population.py live_measurements.csv --save`, `python fit_body_population.py live_body_measurements.csv --save`
3. 바뀐 `face_population.json` / `body_population.json` 만 커밋 → 서버 재시작(재배포)하면 반영. `/health` 의 `face_model` 값으로 확인
   (판정은 서버에서 하므로 앱은 업데이트할 필요 없음)
4. 측정 기준값(`config.py` 의 `FACE_T_*`, `LIVE_*`, `BODY_PLAUSIBLE_DEV` 등)을 바꾸면 `live/measure_*.js`·`live/live_app.js` 맨 위 값도 같이 바꾸고
   `python tests/test_live_parity.py` 로 확인

> 측정 데이터 CSV(`live_*.csv`)에는 팀원 이름과 얼굴·몸 측정값이 들어 있어서 `.gitignore`로 git 에서 뺐습니다.
> 사진은 서버가 디스크에 저장하지 않고 메모리에서만 처리합니다 (R-01).
