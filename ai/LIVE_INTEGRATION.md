# 실시간 분석(C 방식) 연동 가이드 — 백엔드 · 앱 담당자용

> 작성: AI팀 (김민건) · 2026-11-27
> AI 쪽(`ai/`)은 구현·테스트가 끝났습니다. 이 문서는 **백엔드와 앱에서 바꿀 부분**만 정리했습니다.
> 코드는 그대로 붙여 넣을 수 있게 썼지만, 기존 코드 스타일에 맞게 다듬어 주세요.

---

## 0. 한 줄 요약

사진을 찍어 올리는 대신, **휴대폰 화면에서 실시간으로 얼굴·몸을 2번 재고, 잰 숫자만 서버로 보냅니다.**
사진은 휴대폰 밖으로 나가지 않습니다(R-01에 더 유리). 판정은 지금처럼 AI 서버(Python)가 합니다.

```
[앱 WebView] ── AI 서버의 측정 페이지(/live/live.html) 를 띄움
     │  휴대폰 카메라로 실시간 측정 (MediaPipe 웹 버전, 휴대폰 안에서 계산)
     │  측정이 끝나면 postMessage 로 '측정값(숫자)' 전달
     ▼
[앱] ── POST /api/v1/sessions/{id}/analyze/face/live  (JSON)        ← 새 API (백엔드)
     ▼
[백엔드] ── POST {AI}/v1/live/face  (JSON, 체형은 height_cm 추가)    ← AI 서버 (완료)
     ▼
[AI 서버] ── 회차 중앙값 → 판정 → 기존과 같은 응답 형식(부록 A.2/A.3)
     ▼
[백엔드] ── 기존 applyFace / applyBody 그대로 → 앱에 기존 FaceAnalysisData / BodyAnalysisData
```

**응답 형식이 기존 사진 분석과 똑같아서**, 백엔드는 "AI를 부르는 방법"만, 앱은 "촬영 화면"만 바뀝니다.
결과 화면·상세보기·수동 보정·추천은 그대로입니다.

> 용어 풀이
> - **WebView**: 앱 안에 웹페이지를 띄우는 창. Expo Go에 기본 포함(`react-native-webview`)이라 네이티브 빌드가 필요 없습니다.
> - **postMessage**: WebView 안의 웹페이지가 앱(React Native)에 메시지를 보내는 방법.
> - **회차**: 한 번 자세를 잡고 좋은 프레임 5~6장을 모으는 것. 2회차를 재고 중앙값으로 판정합니다(팀 데이터 기준 일치율 약 86%, 사진 1장은 약 78%).

---

## 1. AI 서버가 제공하는 것 (구현 완료)

### 1.1 측정 페이지

| 주소 | 설명 |
|---|---|
| `{AI}/live/live.html?mode=face` | 얼굴형 측정 (전면 카메라) |
| `{AI}/live/live.html?mode=body` | 체형 측정 (전면 카메라, 3초 카운트다운) |
| 선택 파라미터 | `&camera=environment`(후면 카메라), `&rounds=2`(회차 수, 1~3) |

- **https 주소여야 카메라가 켜집니다** (브라우저 보안 규칙). 배포된 AI 서버(Railway)는 https라 그대로 됩니다.
- 페이지가 화면 안내(가까이/뒤로/정면/움직이지 마세요…)·카운트다운·회차 진행을 모두 그립니다. 앱은 띄우기만 하면 됩니다.
- 앱 밖(PC 브라우저)에서 열면 측정 후 결과를 바로 보여 줍니다 → 앱 없이 테스트 가능.

### 1.2 페이지 → 앱 메시지

```jsonc
// 진행 상태 (선택적으로 사용)
{ "type": "moda-live-status", "mode": "face", "status": "loading" | "ready" | "measuring" | "between" | "error",
  "code": "CAMERA_DENIED" | "CAMERA_UNSUPPORTED" | "LOAD_FAILED", "message": "..." }   // code·message 는 error 일 때만

// 측정 완료 — payload 를 그대로 백엔드에 보내면 됨 (앱에서 가공 X)
{ "type": "moda-live-result", "mode": "face",
  "payload": { "rounds": [[{...프레임 측정값}, ...], [...]], "quality": { "yaw": 1.2, ... } } }
```

### 1.3 AI 서버 API (백엔드가 호출)

| Method | URL | 요청 (JSON) | 응답 |
|---|---|---|---|
| `POST` | `{AI}/v1/live/face` | 페이지의 `payload` 그대로 (+ `request_id` 선택) | 부록 A.2와 같음 (+ `n_rounds`, `n_frames`, `round_results`) |
| `POST` | `{AI}/v1/live/body` | 페이지의 `payload` + **`height_cm`(int, 필수)** (+ `request_id`) | 부록 A.3과 같음 (+ 같은 추가 필드) |

- 측정값이 부족하면 HTTP 200 + `usable:false` + `warnings:["LIVE_NOT_ENOUGH_FRAMES"]` (재측정 안내).
- `height_cm` 누락·형식 오류는 422 → 백엔드 규칙상 `502 AI_BAD_RESPONSE`.
- 체형도 `confidence`·`borderline`·`candidates`(4종)를 보냅니다 (11/28 추가). 백엔드는 이미 받을 준비가 되어 있어
  `borderline=true` 면 `BODY_BORDERLINE` 안내가 자동으로 붙고, 상세보기에 체형 후보·확률이 나옵니다.
- 추가 필드(`n_rounds` 등)는 `AiResponseParser` 가 무시하므로 문제없습니다.

---

## 2. 백엔드 변경 (담당: 백엔드)

### 2.1 `AnalysisClient` — 메서드 2개 추가

```java
// com.moda.ai.AnalysisClient
FaceAnalysisResult analyzeFaceLive(JsonNode measurement, String requestId, AnalysisOptions options);

BodyAnalysisResult analyzeBodyLive(JsonNode measurement, int heightCm, String requestId, AnalysisOptions options);
```

### 2.2 `ExternalAiAnalysisClient` — JSON 호출

```java
static final String FACE_LIVE_PATH = "/v1/live/face";
static final String BODY_LIVE_PATH = "/v1/live/body";

@Override
public FaceAnalysisResult analyzeFaceLive(JsonNode measurement, String requestId, AnalysisOptions options) {
    ObjectNode body = liveBody(measurement, requestId);
    return parser.parseFace(parser.readTree(postJson(faceClient, FACE_LIVE_PATH, body), "face"));
}

@Override
public BodyAnalysisResult analyzeBodyLive(JsonNode measurement, int heightCm, String requestId, AnalysisOptions options) {
    ObjectNode body = liveBody(measurement, requestId);
    body.put("height_cm", heightCm);                       // 키는 세션 값을 서버가 붙임 (기존 ④와 같음)
    return parser.parseBody(parser.readTree(postJson(bodyClient, BODY_LIVE_PATH, body), "body"));
}

/** 앱이 보낸 측정값에서 rounds·quality 만 골라 AI 로 전달 (다른 필드는 버림). */
private static ObjectNode liveBody(JsonNode measurement, String requestId) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.set("rounds", measurement.path("rounds"));
    if (measurement.path("quality").isObject()) {
        body.set("quality", measurement.get("quality"));
    }
    if (requestId != null) {
        body.put("request_id", requestId);
    }
    return body;
}
```

`postJson` 은 기존 `post(...)` 와 같은 오류 변환을 쓰고 본문만 JSON 으로 바꾼 것입니다.
기존 `post` 의 `try { ... } catch` 부분을 공용 메서드로 빼서 둘 다 쓰게 하면 깔끔합니다.

```java
private String postJson(RestClient client, String path, JsonNode body) {
    try {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(s -> s.is5xxServerError(), (req, res) -> {
                    throw new BusinessException(ErrorCode.AI_SERVER_UNAVAILABLE, null,
                            new IllegalStateException("AI " + path + " → HTTP " + res.getStatusCode().value()));
                })
                .onStatus(s -> s.is4xxClientError(), (req, res) -> {
                    throw new BusinessException(ErrorCode.AI_BAD_RESPONSE, null,
                            new IllegalStateException("AI " + path + " → HTTP " + res.getStatusCode().value()));
                })
                .body(String.class);
    } catch (BusinessException e) {
        throw e;
    } catch (ResourceAccessException e) {
        throw new BusinessException(isReadTimeout(e) ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_SERVER_UNAVAILABLE, null, e);
    } catch (RestClientException e) {
        log.warn("[AI] {} 응답 처리 실패", path, e);
        throw new BusinessException(ErrorCode.AI_BAD_RESPONSE, null, e);
    }
}
```

필요한 import: `com.fasterxml.jackson.databind.JsonNode`, `com.fasterxml.jackson.databind.node.JsonNodeFactory`, `com.fasterxml.jackson.databind.node.ObjectNode`.

### 2.3 `MockAnalysisClient` — Mock 모드도 그대로 동작하게

Mock 은 사진을 쓰지 않으므로 기존 메서드를 그대로 부르면 됩니다 (Mock 헤더도 그대로 적용).

```java
@Override
public FaceAnalysisResult analyzeFaceLive(JsonNode measurement, String requestId, AnalysisOptions options) {
    return analyzeFace(null, options);
}

@Override
public BodyAnalysisResult analyzeBodyLive(JsonNode measurement, int heightCm, String requestId, AnalysisOptions options) {
    return analyzeBody(null, heightCm, options);
}
```

### 2.4 `GuestSessionService` — 새 메서드 2개 (기존 apply 재사용)

```java
// ③-L 얼굴 실시간 분석
public FaceAnalysisData analyzeFaceLive(String sessionId, JsonNode measurement, AnalysisOptions options) {
    requireActiveSnapshot(sessionId);
    requireMeasurement(measurement);
    AnalysisInProgressRegistry.InFlight token = inProgressRegistry.acquire(sessionId, new byte[0]);  // 사진 없음
    try {
        FaceAnalysisResult result = analysisClient.analyzeFaceLive(measurement, UUID.randomUUID().toString(), options);
        return tx.execute(status -> applyFace(sessionId, result));
    } finally {
        inProgressRegistry.release(token);
    }
}

// ④-L 체형 실시간 분석
public BodyAnalysisData analyzeBodyLive(String sessionId, JsonNode measurement, AnalysisOptions options) {
    GuestSession snapshot = requireActiveSnapshot(sessionId);
    if (!snapshot.hasProfile()) {
        throw new BusinessException(ErrorCode.PROFILE_INPUT_REQUIRED);
    }
    requireMeasurement(measurement);
    int heightCm = snapshot.getHeightCm();
    AnalysisInProgressRegistry.InFlight token = inProgressRegistry.acquire(sessionId, new byte[0]);
    try {
        BodyAnalysisResult result = analysisClient.analyzeBodyLive(measurement, heightCm, UUID.randomUUID().toString(), options);
        return tx.execute(status -> applyBody(sessionId, heightCm, result));
    } finally {
        inProgressRegistry.release(token);
    }
}

private static void requireMeasurement(JsonNode m) {
    if (m == null || !m.path("rounds").isArray() || m.path("rounds").isEmpty()) {
        throw BusinessException.invalidField("rounds", "측정값(rounds)이 필요해요.", null);
    }
}
```

(기존 사진 분석에서 `requestId` 를 만드는 방식이 있으면 그것을 쓰세요.)

### 2.5 `SessionController` — 새 API 2개

```java
@Operation(summary = "③-L 얼굴 실시간 분석", description = "앱 WebView 측정 페이지가 준 payload(JSON) 그대로. 응답은 ③과 같음")
@PostMapping(value = "/{sessionId}/analyze/face/live", consumes = MediaType.APPLICATION_JSON_VALUE)
public ApiResponse<FaceAnalysisData> analyzeFaceLive(
        @PathVariable String sessionId,
        @RequestBody JsonNode measurement,
        @RequestHeader(value = AnalysisOptions.H_FACE_SHAPE, required = false) String mockFaceShape,
        @RequestHeader(value = AnalysisOptions.H_UNUSABLE, required = false) String mockUnusable,
        @RequestHeader(value = AnalysisOptions.H_BORDERLINE, required = false) String mockBorderline,
        @RequestHeader(value = AnalysisOptions.H_DELAY_MS, required = false) String mockDelayMs) {
    AnalysisOptions options = new AnalysisOptions(mockFaceShape, null, mockUnusable, mockBorderline, null, mockDelayMs);
    return ApiResponse.success(service.analyzeFaceLive(sessionId, measurement, options));
}

@Operation(summary = "④-L 체형 실시간 분석", description = "세션 heightCm 를 서버가 자동 사용. 응답은 ④와 같음")
@PostMapping(value = "/{sessionId}/analyze/body/live", consumes = MediaType.APPLICATION_JSON_VALUE)
public ApiResponse<BodyAnalysisData> analyzeBodyLive(
        @PathVariable String sessionId,
        @RequestBody JsonNode measurement,
        @RequestHeader(value = AnalysisOptions.H_BODY_TYPE, required = false) String mockBodyType,
        @RequestHeader(value = AnalysisOptions.H_UNUSABLE, required = false) String mockUnusable,
        @RequestHeader(value = AnalysisOptions.H_BORDERLINE, required = false) String mockBorderline,
        @RequestHeader(value = AnalysisOptions.H_BODY_MINIMAL, required = false) String mockBodyMinimal,
        @RequestHeader(value = AnalysisOptions.H_DELAY_MS, required = false) String mockDelayMs) {
    AnalysisOptions options = new AnalysisOptions(null, mockBodyType, mockUnusable, mockBorderline, mockBodyMinimal, mockDelayMs);
    return ApiResponse.success(service.analyzeBodyLive(sessionId, measurement, options));
}
```

### 2.6 경고 문구 (선택, `seed/analysis-labels.json` `warnings`)

새 코드가 없어도 동작합니다(“촬영 상태를 한 번 더 확인해 주세요” + 재측정 안내). 문구를 붙이려면:

| 코드 | target | severity | message 예시 |
|---|---|---|---|
| `LIVE_NOT_ENOUGH_FRAMES` | `COMMON` | `RETAKE` | 측정이 충분히 되지 않았어요. 안내에 맞춰 다시 측정해 주세요. |
| `BODY_LEGS_CLOSED` | `BODY` | `INFO` | 다리를 살짝 벌리면 다리 비율까지 더 정확하게 볼 수 있어요. |

### 2.7 `docs/api-spec.md` 에 추가할 내용

- 5장에 ③-L, ④-L (요청 = 1.2의 `payload`, 응답 = ③·④와 동일, 에러 = ③·④와 동일)
- 부록 A에 A.7 `/v1/live/face`, `/v1/live/body` (이 문서 1.3 표)

### 2.8 배포 설정

기존과 같습니다: `MODA_AI_MODE=external`, `MODA_AI_BASE_URL=<AI 서버 주소>`. 타임아웃(얼굴 15초·전신 20초)도 그대로면 충분합니다 (판정만 하므로 50ms 이내).

---

## 3. 앱 변경 (담당: 이상현 님)

### 3.1 설치 · 설정

```powershell
cd mobile
npx.cmd expo install react-native-webview
```

`.env` (또는 `client.ts` 기본값)에 AI 측정 페이지 주소:

```
EXPO_PUBLIC_AI_LIVE_URL=https://<AI 서버 Railway 주소>/live/live.html
```

> 측정 페이지는 **AI 서버**에 있고, 분석 API 는 지금처럼 **백엔드**를 부릅니다. 주소가 두 개인 점만 주의해 주세요.

### 3.2 `modaApi.ts` — 함수 2개 추가

```ts
// ③-L 얼굴 실시간 분석 (측정 페이지 payload 를 그대로 전송)
analyzeFaceLive: (sessionId: string, payload: LiveMeasurement, mock?: MockHeadersConfig, baseUrl?: string) =>
  apiRequest<FaceAnalysisData>(`/api/v1/sessions/${sessionId}/analyze/face/live`, {
    method: 'POST',
    body: payload,
    headers: buildMockHeaders(mock, 'face'),
    baseUrl,
    timeoutMs: 30_000,
    timeoutCode: 'AI_TIMEOUT',
    networkUnavailableCode: 'AI_SERVER_UNAVAILABLE',
  }),

// ④-L 체형 실시간 분석 (키는 백엔드가 세션 값으로 붙임)
analyzeBodyLive: (sessionId: string, payload: LiveMeasurement, mock?: MockHeadersConfig, baseUrl?: string) =>
  apiRequest<BodyAnalysisData>(`/api/v1/sessions/${sessionId}/analyze/body/live`, {
    method: 'POST',
    body: payload,
    headers: buildMockHeaders(mock, 'body'),
    baseUrl,
    timeoutMs: 30_000,
    timeoutCode: 'AI_TIMEOUT',
    networkUnavailableCode: 'AI_SERVER_UNAVAILABLE',
  }),
```

`types.ts` 에 추가:

```ts
/** AI 측정 페이지가 보내는 실시간 측정값 — 앱은 가공하지 않고 그대로 백엔드에 전달 */
export interface LiveMeasurement {
  rounds: Array<Array<Record<string, number | null>>>;
  quality?: Record<string, number>;
}
```

### 3.3 새 컴포넌트 `src/components/LiveAnalysisView.tsx`

`ai/integration/LiveAnalysisView.tsx` 파일을 그대로 복사해 쓰면 됩니다. 하는 일:

- iOS·Android: `WebView` 로 측정 페이지를 띄우고 메시지를 받음
- 웹(Expo web): `iframe` 으로 띄우고 `window.message` 로 받음
- `onResult(payload)` · `onError(code)` 콜백, `reloadKey` 를 바꾸면 처음부터 다시 측정

### 3.4 `FaceCaptureScreen` 바꾸는 방법 (`BodyCaptureScreen` 도 같은 방식)

1. 지금 `uploadAndAnalyze(uri)` 안의 **응답 처리 부분**을 `handleFaceResponse(data)` 로 분리합니다.
   (usable=false Alert, `setFaceAnalysis`, INFO 경고 Alert, `nextStep` 이동 — 지금 코드 그대로)
2. 카메라(`CameraView`) 자리에 `LiveAnalysisView` 를 둡니다.

```tsx
const [liveKey, setLiveKey] = useState(0);
const [useLive, setUseLive] = useState(true);   // 측정 페이지를 못 쓰면 기존 사진 촬영으로

const handleLiveResult = async (payload: LiveMeasurement) => {
  if (!sessionId) return handleSessionExpired('세션을 찾을 수 없어요. 처음부터 다시 시작해 주세요.');
  setAnalyzing(true);
  try {
    const data = await modaApi.analyzeFaceLive(sessionId, payload, mockConfig, baseUrl);
    if (!data.usable) {
      Alert.alert('다시 측정해 주세요', data.warnings.map((w) => w.message).join('\n'),
        [{ text: '다시 측정', onPress: () => setLiveKey((k) => k + 1) }]);
      return;
    }
    handleFaceResponse(data);      // 1번에서 분리한 기존 처리
  } catch (err) {
    // 기존 catch 와 같음 — 503/504 는 [재시도: handleLiveResult(payload)] [직접 선택하기]
    // 그 외 오류는 setLiveKey(k => k + 1) 로 다시 측정
  } finally {
    setAnalyzing(false);
  }
};

// 렌더링 (카메라 권한은 지금처럼 useCameraPermissions 로 먼저 받은 뒤)
{useLive ? (
  <LiveAnalysisView
    mode="face"
    reloadKey={liveKey}
    onResult={handleLiveResult}
    onError={() => setUseLive(false)}      // 카메라·모델 문제 → 기존 사진 촬영 화면으로
  />
) : (
  /* 기존 CameraView + 셔터 UI 그대로 */
)}
```

- 촬영 사진이 없으므로 `setLocalFaceUri(uri)` 는 부르지 않습니다 (상세보기 썸네일 자리는 숨김).
- 기존 카운트다운·셔터·앨범 버튼은 `useLive=false`(대체 경로)일 때만 보입니다.
- 체형은 `mode="body"`, `analyzeBodyLive`, `BodySilhouetteGuide` 는 페이지가 안내를 그리므로 실시간 모드에서는 숨김.
  페이지는 **팔을 몸에서 살짝 뗀 A자 자세**가 되어야 측정을 시작합니다.

### 3.5 휴대폰 조건

- **iOS 16.4 이상** (MediaPipe 웹 버전이 쓰는 WebAssembly 기능), Android Chrome 최신.
- Expo Go 에서 그대로 동작합니다 (`react-native-webview` 는 Expo Go 에 포함).
- 처음 열 때 모델·엔진(약 25MB)을 받느라 몇 초 걸리고, 이후에는 캐시됩니다. 부스 시연 전 한 번 열어 두세요.

---

## 4. 테스트 순서

1. **AI 서버 단독 (PC)** — `ai` 폴더에서 `uvicorn server:app --port 8000` →
   브라우저로 `http://localhost:8000/live/live.html?mode=face` (체형은 `?mode=body&height=175`).
   측정이 끝나면 화면에 판정 결과가 나옵니다. (PC 의 localhost 는 http 여도 카메라가 켜짐)
2. **휴대폰 Safari 로 페이지만** — https 주소가 필요합니다. AI 서버를 Railway 에 배포하거나,
   PC 에서 `cloudflared tunnel --url http://localhost:8000` 으로 임시 https 주소를 만들어 Safari 로 열어 봅니다.
3. **백엔드 연결** — 2장 적용 후 Swagger 에서 ③-L 을 Mock 모드로 호출 → external 모드로 실제 호출.
4. **앱** — 3장 적용 후 Expo Go 로 전체 흐름.

### 확인된 것 (AI팀)

- 휴대폰 페이지의 측정 계산이 Python 과 같은 값을 냄: 얼굴 5개 값·고개 각도 **완전히 일치**, 체형은 키 대비 0.1% 이내
  (`python tests/test_live_parity.py <사진·영상>`, Node.js 필요)
- Chrome 에 테스트 영상을 가짜 카메라로 넣어 측정 페이지 전체 흐름(안내 → 2회차 → 판정) 확인, Python `run_live*.py` 와 같은 판정
- `/v1/live/*` 응답이 백엔드 `AiResponseParser` 규칙을 지킴 (`python tests/test_api_contract.py`)
