# Moda 통합 REST API 명세서 (`docs/api-spec.md`)

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.2 (API `v1`, 추천 규칙 `ruleVersion: "v1.1-draft"`, AI 얼굴 분석 `v8` 대응) |
| 작성 | 백엔드 박준희 |
| 대상 | 프론트엔드 이상현(Expo), AI 김민건·최기완 |
| 관련 문서 | `docs/style-rules.md` (Enum·규칙·문구 정의), `docs/recommendation-dataset.csv` |
| 범위 | 대회 MVP — 비로그인 게스트 세션 기반 API 10종(분석·결과 8종 + 챗봇 2종) + 부록(백엔드↔AI 내부 연동 스펙, TypeScript 타입) |

---

## 0. 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| v1.0 | 2026-11-19 | 최초 작성 (API 8종, 공통 에러 포맷, 화면 매핑, Mock 헤더, AI 연동 스펙) |
| v1.1 | 2026-11-20 | 맞춤 유형 24종(`TYPE-01`~`TYPE-24`) 반영 및 얼굴형 그룹 필드 삭제(`basis`, TS 타입), 유형명 확정 여부 필드명을 `nameConfirmed`로 통일, 분석 불가 사진은 재촬영만 허용(“직접 선택하기”는 AI 장애 503/504 시에만), 체형 AI 응답의 부가 필드 선택화(`null`/`[]` 허용), `X-Mock-Body-Minimal` 헤더 추가, 사진 메모리 처리 원칙 명시 |
| v1.2 | 2026-11-25 | 결과 화면 이후 **[챗봇 이용하기]** 기능 반영: 챗봇 API 2종(⑨ 대화 진입 조회, ⑩ 메시지 전송) 추가, 에러 코드 3종(`CHAT_TURN_LIMIT_EXCEEDED`, `CHAT_SERVER_UNAVAILABLE`, `CHAT_TIMEOUT`) 추가, 세션 삭제·만료 시 챗봇 대화 내역(`chat_message`) 즉시 삭제, `moda.chat.mode` Mock 설정 추가, 화면↔API 매핑 갱신. AI `v8` 대응: 얼굴 분석 응답 `reason`(선택) 필드 및 `FaceDetail.aiReason` 추가. 기존 API ①~⑧의 요청·응답 구조는 변경 없음(`FaceDetail`에 `aiReason` 필드 추가만 해당) |

---

## 1. 공통 규칙

### 1.1 서버 주소

| 환경 | Base URL | 비고 |
|---|---|---|
| 로컬 (PC 브라우저·iOS 시뮬레이터) | `http://localhost:8080` | |
| 로컬 (Android 에뮬레이터) | `http://10.0.2.2:8080` | 에뮬레이터에서 PC localhost 접근 주소 |
| 로컬 (실기기 Expo Go) | `http://{PC의 LAN IP}:8080` | PC와 휴대폰이 같은 Wi-Fi |
| Mock 배포 서버 | Step 3에서 확정 후 이 표에 기입 | `moda.ai.mode=mock` |
| 대회 서버 | Step 4에서 확정 후 이 표에 기입 | `moda.ai.mode=external` |

- 모든 API 경로 앞에 `/api/v1`이 붙습니다.
- Swagger UI: `{Base URL}/swagger-ui/index.html` / OpenAPI JSON: `{Base URL}/v3/api-docs`
- CORS: 모든 Origin 허용(MVP), 허용 메서드 `GET, POST, PATCH, DELETE, OPTIONS`.

### 1.2 요청 형식

| 구분 | 규칙 |
|---|---|
| JSON 요청 | `Content-Type: application/json; charset=UTF-8` |
| 사진 업로드 | `Content-Type: multipart/form-data` (파트 이름 `image`, 1장) |
| 사진 제한 | `image/jpeg`, `image/png`만 허용, **최대 10MB** (요청 전체 12MB) |
| 인증 | 없음 (MVP). 세션 식별은 URL의 `{sessionId}`(UUID v4 문자열) |
| 필드 표기 | 요청·응답 JSON 필드는 **camelCase** |
| Enum 표기 | 얼굴형·체형 Key는 **소문자** (`heart`, `small_inv_triangle`), 그 외 Enum은 **대문자 스네이크** (`SEMI_CURLY`, `PARTED_STYLE`) |
| 날짜·시간 | ISO-8601, KST 오프셋 포함 문자열. 예: `2026-11-19T21:30:00+09:00` |
| 숫자 | `age`, `heightCm`, `weightKg`, `score`, `rank`는 정수. `confidence`, `probability`, `deviation`은 0~1 또는 ±소수 (소수 셋째 자리까지) |

### 1.3 응답 형식 (공통 Envelope)

모든 응답은 아래 두 형태 중 하나입니다.

성공:
```json
{
  "success": true,
  "data": { }
}
```

실패: (4장 공통 에러 포맷)
```json
{
  "success": false,
  "error": { }
}
```

- 성공 시 `error` 키는 없고, 실패 시 `data` 키는 없습니다.
- 응답 객체의 키는 **항상 존재**합니다. 값이 없으면 키를 생략하지 않고 `null`을 넣습니다. 배열은 값이 없으면 `null`이 아니라 `[]`입니다.
- 화면에 표시할 한글 텍스트(라벨·유형명·추천 이유·경고 메시지)는 **모두 응답에 포함**되어 있습니다. 프론트엔드는 한글 문구를 하드코딩하지 않고 응답 값을 그대로 표시합니다.

### 1.4 세션 수명·사진 처리 정책

| 항목 | 정책 |
|---|---|
| 세션 TTL | 30분, **쓰기 요청(POST/PATCH) 성공 시마다 30분 연장** (응답의 `expiresAt` 갱신). ⑩ 챗봇 메시지 전송도 성공 시 연장되며, ⑨ 대화 조회(GET)는 연장하지 않음 |
| 만료 세션 | 만료 후 접근 시 `410 SESSION_EXPIRED`. 5분 주기 스케줄러가 만료 세션과 관련 데이터(입력값·분석 결과·**챗봇 대화 내역 `chat_message`**)를 삭제 |
| 챗봇 대화 보관 | 대화 내역(`chat_message`)은 **세션 수명 동안만** DB에 보관합니다. 세션 삭제(⑧)·만료 시 해당 세션의 대화 내역을 **즉시 함께 삭제**합니다(세션 FK, `ON DELETE CASCADE`). 사진·이미지는 LLM에 전달하지 않으며, 텍스트 데이터만 사용합니다. |
| 사진 보관 | 업로드 사진은 **디스크에 저장하지 않고 메모리(`byte[]`)에서 바로 AI 서버로 전달하는 방식을 우선** 적용합니다. 구현상 임시 파일이 불가피한 경우에도 분석이 끝나는 즉시(성공·실패·예외 모두, `finally` 블록) 삭제합니다. DB에 사진·경로·랜드마크 좌표를 저장하지 않습니다. (R-01) |
| 사진 반환 | 서버는 사진을 다시 내려주지 않습니다. 결과 화면에 사진을 보여주려면 앱의 로컬 URI를 사용합니다. |
| 세션 초기화 | `DELETE /api/v1/sessions/{sessionId}` 호출 시 세션·입력값·분석 결과·챗봇 대화 내역·잔여 임시 사진을 즉시 파기 |
| 회원 확장 | 세션 엔티티에 `userId`(nullable) 컬럼이 예약되어 있으며, 2차 목표에서 로그인 시 세션을 사용자에게 귀속합니다. MVP 응답에는 노출하지 않습니다. |

### 1.5 Mock 모드 테스트 헤더 (프론트엔드 테스트용)

서버가 `moda.ai.mode=mock`일 때만 동작하고, `external` 모드에서는 무시됩니다. `POST .../analyze/face`, `POST .../analyze/body`에 붙입니다. (단, `X-Mock-Delay-Ms`는 `moda.chat.mode=mock`일 때 ⑩ 챗봇 메시지 전송에도 적용 — 아래 챗봇 Mock 참고)

| 헤더 | 값 | 효과 | 기본값(헤더 없음) |
|---|---|---|---|
| `X-Mock-Face-Shape` | 얼굴형 Key 6종 | 지정한 얼굴형으로 분석 결과 반환 | `oval` |
| `X-Mock-Body-Type` | 체형 Key 4종 | 지정한 체형으로 분석 결과 반환 | `standard` |
| `X-Mock-Unusable` | `true` | `usable=false` + 재촬영 경고 반환 (얼굴: `FACE_YAW_EXCEEDED`, 전신: `BODY_LOW_VISIBILITY`) | `false` |
| `X-Mock-Borderline` | `true` | `borderline=true`, `confidence=0.52` + `*_BORDERLINE` 안내 경고 | `false` |
| `X-Mock-Body-Minimal` | `true` | 체형 응답을 **필수 필드만** 있는 형태로 반환 (`confidence`·`borderline`=`null`, `candidates`·`features`=`[]`, `compareToSizeKorea`=`null`). 유료 API 미확정 대비 화면 테스트용 | `false` |
| `X-Mock-Delay-Ms` | 0 ~ 10000 | 응답 지연(로딩 UI 테스트). **⑩ 챗봇 메시지 전송에도 적용** (아래 챗봇 Mock 참고) | `800` |

- Mock 얼굴 분석은 얼굴형별 고정 `reason` 문장을 함께 반환합니다(⑥ `face.aiReason`에 반영, 부록 A.2).

**챗봇 Mock 모드 (`moda.chat.mode`)**

챗봇 LLM 호출은 AI 분석과 **별도 설정**으로 전환합니다. 두 설정은 서로 독립이며, 예를 들어 `moda.ai.mode=external` + `moda.chat.mode=mock` 조합도 가능합니다.

| 설정 (`application.yml`) | 값 | 동작 |
|---|---|---|
| `moda.chat.mode` | `mock` | LLM을 호출하지 않고, 질문 키워드·세션 결과(유형·얼굴형·체형·추천 스타일)를 조합한 **고정 템플릿 답변**을 반환. API 키 없이 챗봇 화면 테스트 가능 |
| | `external` | Spring AI `ChatClient`로 실제 LLM 호출 (대회 서버) |
| `moda.chat.max-turns` | `20` | 세션당 최대 대화 턴 수 |
| `moda.chat.timeout` | 연결 3초 / 응답 20초 | 초과 시 `504 CHAT_TIMEOUT` |

- `moda.chat.mode=mock`일 때 `X-Mock-Delay-Ms`를 ⑩ `POST .../chat/messages`에 붙이면 답변 지연(“입력 중…” 말풍선 UI) 테스트가 가능합니다. 헤더가 없으면 기본 `800`ms 지연입니다. `external` 모드에서는 무시됩니다.
- ⑨ `GET .../chat/messages`는 LLM을 호출하지 않으므로(첫 인사·추천 질문은 서버 템플릿) 모드와 관계없이 즉시 응답합니다.
- Mock 모드에서도 턴 수 차감, DB 저장, 세션 TTL 연장, `429 CHAT_TURN_LIMIT_EXCEEDED`는 실제와 동일하게 동작합니다.

---

## 2. 공통 Enum 요약

정의·한글명·설명 문구의 원본은 `docs/style-rules.md` 1장입니다. 아래 값 외의 문자열을 보내면 `400 INVALID_REQUEST`입니다.

| Enum | 값 (한글명) |
|---|---|
| `FaceShapeKey` | `diamond`(다이아몬드형), `heart`(하트형), `oval`(계란형), `rectangle`(직사각형), `round`(둥근형), `long`(긴형) |
| `BodyTypeKey` | `standard`(표준유형), `small_inv_triangle`(작은 역삼각형), `large_rectangle`(큰 사각 체형), `rectangle`(사각 체형) |
| `HairThickness` | `THIN`, `MEDIUM`, `THICK` |
| `HairCurl` | `STRAIGHT`, `SEMI_CURLY`, `CURLY` |
| `HairDensity` | `LOW`, `MEDIUM`, `HIGH` |
| `HairStyleCode` (응답 전용) | `UP_STYLE`(깐머리 스타일), `PARTED_STYLE`(가르마 스타일), `DANDY_STYLE`(댄디 스타일), `CROP_STYLE`(크롭 스타일), `SHADOW_STYLE`(쉐도우 스타일) |
| `FashionStyleCode` (응답 전용) | `CASUAL`(캐주얼), `DANDY`(댄디), `MINIMAL`(미니멀), `CITY_BOY`(시티보이), `AMEKAJI`(아메카지), `STREET`(스트릿), `SPORTY`(스포티), `GORPCORE`(고프코어), `VINTAGE`(빈티지) |
| `NextStep` (응답 전용) | `PROFILE_INPUT`, `FACE_ANALYSIS`, `BODY_ANALYSIS`, `RESULT_READY` |
| `AnalysisSource` (응답 전용) | `AI`(AI 분석값), `MANUAL`(사용자 직접 선택값) |
| `AnalysisStatus` (응답 전용) | `ANALYZED`(AI 결과 사용 중), `CORRECTED`(AI 결과를 사용자가 수정), `MANUAL`(AI 결과 없이 사용자가 직접 선택), `NOT_ANALYZED`(미분석) |
| `WarningSeverity` (응답 전용) | `RETAKE`(재촬영 필요 원인), `INFO`(참고 안내) |
| `SizeKoreaDirection` (응답 전용) | `ABOVE`(평균보다 큼), `BELOW`(평균보다 작음), `SIMILAR`(평균과 비슷, \|deviation\| < 0.02) |
| `ChatRole` (응답 전용) | `USER`(사용자 메시지), `ASSISTANT`(챗봇 답변) |

`nextStep` 결정 규칙 (모든 세션 응답 공통): `profileInput`이 false → `PROFILE_INPUT` / 그다음 `faceAnalysis`가 false → `FACE_ANALYSIS` / 그다음 `bodyAnalysis`가 false → `BODY_ANALYSIS` / 모두 true → `RESULT_READY`

---

## 3. 공통 객체 스키마

### 3.1 `CodeLabel`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `key` | string | 불가 | Enum 값 (예: `heart`) |
| `label` | string | 불가 | 화면 표시 한글명 (예: `하트형`) |

### 3.2 `Progress`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `profileInput` | boolean | 불가 | 기본 정보 입력 완료 여부 |
| `faceAnalysis` | boolean | 불가 | 얼굴형 확정 여부 (AI 분석 성공 또는 수동 선택) |
| `bodyAnalysis` | boolean | 불가 | 체형 확정 여부 (AI 분석 성공 또는 수동 선택) |

### 3.3 `Warning`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `code` | string | 불가 | 경고 코드 (부록 A.5 표) |
| `severity` | `WarningSeverity` | 불가 | `RETAKE` 또는 `INFO` |
| `message` | string | 불가 | 사용자에게 그대로 보여줄 안내 문구 |

### 3.4 `HairRecommendation`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `rank` | int | 불가 | 1 또는 2 |
| `code` | `HairStyleCode` | 불가 | |
| `name` | string | 불가 | 공식 명칭 (예: `가르마 스타일`) |
| `description` | string | 불가 | 스타일 정의 1문장 |
| `imageKey` | string | 불가 | 앱 로컬 에셋 키 (예: `hair_parted_style`) |
| `score` | int | 불가 | 순위 산정 점수. **화면 표시 금지** |
| `reasons` | string[] | 불가 | 추천 이유 1~2문장 (화면 표시용) |
| `reasonCodes` | string[] | 불가 | `reasons`와 같은 순서의 근거 코드 |
| `scoreBreakdown` | `ScoreBreakdown` | 불가 | 점수 계산 내역 (디버깅·시연 설명용, 화면 표시 선택) |

### 3.5 `FashionRecommendation`

`HairRecommendation`의 모든 필드 + 아래 필드. `rank`는 1~3, `code`는 `FashionStyleCode`.

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `itemKeywords` | string[] | 불가 | 추천 아이템 키워드 5개 (예: `["블레이저","셔츠","니트 폴로","슬랙스","로퍼"]`) |

### 3.6 `ScoreBreakdown`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `base` | int | 불가 | 1차 기준 기본 점수 |
| `adjustments` | `Adjustment[]` | 불가 | delta ≠ 0 인 보정만 포함, 없으면 `[]` |

`Adjustment`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `code` | string | 불가 | 보정 코드 (예: `ADJ_CURL_SEMI_CURLY_PARTED_STYLE`) |
| `factor` | string | 불가 | 헤어: `hairThickness` / `hairCurl` / `hairDensity`, 패션: `bmi` / `height` / `age` |
| `value` | string | 불가 | 헤어: 입력 Enum 값, 패션: 구간명. 단 `factor=bmi`이면 항상 `"HIDDEN"` (R-11) |
| `delta` | int | 불가 | 가감 점수 (±) |

### 3.7 `Candidate`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `key` | string | 불가 | 얼굴형 또는 체형 Key |
| `label` | string | 불가 | 공식 한글명 |
| `probability` | number | 불가 | 0~1, 내림차순 정렬 |

### 3.8 `Feature`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `key` | string | 불가 | AI 특징값 키 (예: `length_ratio`) |
| `label` | string | 불가 | 화면 라벨 (예: `얼굴 길이 비율`). 백엔드에 라벨이 없는 키는 `key`를 그대로 넣음 |
| `value` | number | 불가 | 측정값 |
| `unit` | string | 불가 | `ratio` / `deg` / `percent` |
| `description` | string | 가능 | 계산 방법 설명 (예: `얼굴 길이 ÷ 광대 폭`) |

### 3.9 `SizeKoreaCompare` (체형 전용, AI가 제공할 때만 존재)

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `reference` | string | 불가 | 기준 데이터 코드 (`SIZE_KOREA_20S_MALE`) |
| `referenceLabel` | string | 불가 | `Size Korea 20대 남성 평균` |
| `heightCm` | int | 불가 | 비교에 사용한 사용자 키 |
| `items` | `SizeKoreaItem[]` | 불가 | 항목별 비교 |
| `summary` | object | 가능 | `{ "shoulderDeviation": number, "trunkDeviation": number }`. AI가 해당 값을 주지 않으면 `null` |

`SizeKoreaItem`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `key` | string | 불가 | `bideltoid_width` / `waist_width` / `hip_width` / `shoulder_height` / `crotch_height` / `arm_length` |
| `label` | string | 불가 | 화면 라벨 (부록 A.4) |
| `userMm` | int | 불가 | 사진에서 추정한 사용자 치수(mm) |
| `refMm` | int | 불가 | Size Korea 평균 치수(mm) |
| `deviation` | number | 불가 | 키 대비 편차. `+0.04` = 평균보다 4% 큼 |
| `deviationPercent` | number | 불가 | `deviation × 100`, 소수 첫째 자리 |
| `direction` | `SizeKoreaDirection` | 불가 | `ABOVE` / `BELOW` / `SIMILAR` |

화면 문구 예: `SIMILAR` → “평균과 비슷해요”, `ABOVE` → “평균보다 4.0% 커요”, `BELOW` → “평균보다 4.1% 작아요”

### 3.10 `ProfileInput`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `age` | int | 불가 | 14 ~ 80 |
| `heightCm` | int | 불가 | 140 ~ 210 |
| `weightKg` | int | 가능 | 30 ~ 200, 미입력 시 `null` |
| `hairThickness` | `HairThickness` | 불가 | |
| `hairCurl` | `HairCurl` | 불가 | |
| `hairDensity` | `HairDensity` | 불가 | |

### 3.11 `ResultData` (`GET /result`, `PATCH /profile` 공통)

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `ruleVersion` | string | 불가 | 추천 규칙 버전 |
| `generatedAt` | string | 불가 | 산출 시각 |
| `type` | `TypeResult` | 불가 | 맞춤 유형 |
| `basis` | `ResultBasis` | 불가 | 산출 근거가 된 얼굴형·체형 |
| `hairStyles` | `HairRecommendation[]` | 불가 | 항상 2개, `rank` 오름차순 |
| `fashionStyles` | `FashionRecommendation[]` | 불가 | 항상 3개, `rank` 오름차순 |
| `disclaimer` | string | 불가 | 하단 고정 안내 문구 |

`TypeResult`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `typeCode` | string | 불가 | `TYPE-01` ~ `TYPE-24` (`bodyIndex × 6 + faceIndex + 1`, `style-rules.md` 5.1) |
| `typeName` | string | 불가 | 유형명 (현재 가칭) |
| `nameConfirmed` | boolean | 불가 | 팀 확정 유형명이면 `true` (시드 파일 필드명과 동일) |
| `headline` | string | 불가 | `당신은 {typeName} 유형입니다` 완성 문자열 |
| `description` | string | 불가 | 유형 설명 1~2문장 |
| `stylePoints` | string[] | 불가 | 스타일 포인트 2개 이상 |

`ResultBasis`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `faceShape` | `CodeLabel` | 불가 | |
| `bodyType` | `CodeLabel` | 불가 | |
| `faceSource` | `AnalysisSource` | 불가 | |
| `bodySource` | `AnalysisSource` | 불가 | |

---

## 4. 공통 에러 포맷

```json
{
  "success": false,
  "error": {
    "status": 400,
    "code": "INVALID_REQUEST",
    "message": "입력값을 확인해 주세요.",
    "details": [
      { "field": "heightCm", "reason": "140 이상 210 이하의 정수로 입력해 주세요.", "rejectedValue": 120 }
    ],
    "meta": null,
    "path": "/api/v1/sessions/3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01/profile-input",
    "timestamp": "2026-11-19T21:31:05+09:00"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `status` | int | 불가 | HTTP 상태 코드와 동일 |
| `code` | string | 불가 | 에러 코드 (아래 표). **프론트 분기는 `code`로 합니다** |
| `message` | string | 불가 | 사용자에게 보여줄 수 있는 한글 메시지 |
| `details` | `FieldError[]` | 불가 | 필드 검증 오류 목록, 없으면 `[]` |
| `meta` | object | 가능 | 에러별 추가 정보 (예: `ANALYSIS_INCOMPLETE`의 `missing`, `nextStep`) |
| `path` | string | 불가 | 요청 경로 |
| `timestamp` | string | 불가 | 발생 시각 |

`FieldError`: `{ "field": string, "reason": string, "rejectedValue": any | null }`

### 4.1 에러 코드표

| HTTP | code | 발생 API | 기본 message | 프론트 처리 권장 |
|---|---|---|---|---|
| 400 | `INVALID_REQUEST` | 전체 | 입력값을 확인해 주세요. | `details[].field` 옆에 `reason` 표시 |
| 400 | `PHOTO_CONSENT_REQUIRED` | ① guest | 사진 분석 동의가 필요해요. | 동의 체크 유도 |
| 400 | `IMAGE_REQUIRED` | ③ face, ④ body | 사진을 첨부해 주세요. | 촬영 화면 유지 |
| 404 | `SESSION_NOT_FOUND` | ②~⑦, ⑨·⑩ chat | 체험 세션을 찾을 수 없어요. 처음부터 다시 시작해 주세요. | 로컬 `sessionId` 삭제 → 시작 화면 |
| 409 | `PROFILE_INPUT_REQUIRED` | ④ body, ⑦ patch(모질 수정 시), ⑨·⑩ chat | 기본 정보를 먼저 입력해 주세요. | 기본 정보 화면으로 이동 |
| 409 | `ANALYSIS_INCOMPLETE` | ⑤ result, ⑨·⑩ chat | 얼굴과 전신 분석이 모두 필요해요. | `meta.nextStep` 화면으로 이동 |
| 409 | `ANALYSIS_IN_PROGRESS` | ③ face, ④ body | 분석이 진행 중이에요. 잠시만 기다려 주세요. | 버튼 연타 방지, 무시 |
| 410 | `SESSION_EXPIRED` | ②~⑦, ⑨·⑩ chat | 체험 시간이 지나 세션이 종료되었어요. 처음부터 다시 시작해 주세요. | 로컬 `sessionId` 삭제 → 시작 화면 |
| 413 | `IMAGE_TOO_LARGE` | ③ face, ④ body | 사진 용량은 10MB 이하만 가능해요. | 압축 후 재전송 (`quality: 0.7` 권장) |
| 415 | `UNSUPPORTED_IMAGE_TYPE` | ③ face, ④ body | JPG 또는 PNG 사진만 분석할 수 있어요. | 카메라 포맷 `jpeg` 지정 |
| 429 | `CHAT_TURN_LIMIT_EXCEEDED` | ⑩ chat | 이번 체험에서 대화할 수 있는 횟수(20회)를 모두 사용했어요. | 입력창·추천 질문 칩 비활성화 + 안내 문구 표시 (`meta.maxTurns`, `meta.usedTurns`) |
| 500 | `INTERNAL_ERROR` | 전체 | 일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요. | 재시도 버튼 |
| 502 | `AI_BAD_RESPONSE` | ③ face, ④ body | 분석 결과를 처리하지 못했어요. 다시 촬영해 주세요. | 재촬영 |
| 503 | `AI_SERVER_UNAVAILABLE` | ③ face, ④ body | 분석 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요. | 재시도 + “직접 선택하기” 노출 |
| 503 | `CHAT_SERVER_UNAVAILABLE` | ⑩ chat | 챗봇 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요. | 입력한 문장 유지 + [다시 보내기] (턴 차감 없음) |
| 504 | `AI_TIMEOUT` | ③ face, ④ body | 분석 시간이 초과되었어요. 다시 시도해 주세요. | 재시도 + “직접 선택하기” 노출 |
| 504 | `CHAT_TIMEOUT` | ⑩ chat | 답변 생성 시간이 초과되었어요. 다시 시도해 주세요. | 입력한 문장 유지 + [다시 보내기] (턴 차감 없음) |

> 사진이 분석 불가(얼굴 미검출, 고개 돌림 등)인 경우는 **에러가 아닙니다.** HTTP 200 + `usable: false` + `warnings`로 응답합니다(API ③·④ 참고).
> 404와 410은 프론트에서 같은 처리(처음부터 다시 시작)를 하면 됩니다.
> 챗봇 `503`·`504`는 분석 API의 `503`·`504`와 달리 “직접 선택하기”를 노출하지 않습니다. 실패한 메시지는 저장되지 않고 남은 턴도 줄지 않습니다.

---

## 5. API 상세

| # | Method | URL | 설명 |
|---|---|---|---|
| ① | `POST` | `/api/v1/sessions/guest` | 공개 체험(게스트) 세션 생성 |
| ② | `POST` | `/api/v1/sessions/{sessionId}/profile-input` | 나이·키·몸무게·모질 3항목 저장 |
| ③ | `POST` | `/api/v1/sessions/{sessionId}/analyze/face` | 얼굴 사진 분석 (얼굴형 6종) |
| ④ | `POST` | `/api/v1/sessions/{sessionId}/analyze/body` | 전신 사진 분석 (체형 4종, 세션 `heightCm` 사용) |
| ⑤ | `GET` | `/api/v1/sessions/{sessionId}/result` | 결과 화면: 맞춤 유형 + 추천 헤어 2 + 추천 패션 3 |
| ⑥ | `GET` | `/api/v1/sessions/{sessionId}/analysis-detail` | [분석 상세보기] 팝업: 얼굴형·체형 상세 |
| ⑦ | `PATCH` | `/api/v1/sessions/{sessionId}/profile` | 얼굴형·체형·모질 수동 보정 + 결과 재산출 |
| ⑧ | `DELETE` | `/api/v1/sessions/{sessionId}` | 세션·임시 사진 즉시 초기화 (부스 시연용) |
| ⑨ | `GET` | `/api/v1/sessions/{sessionId}/chat/messages` | 챗봇 화면 진입: 대화 내역 + 맞춤 첫 인사 + 추천 질문 + 남은 턴 |
| ⑩ | `POST` | `/api/v1/sessions/{sessionId}/chat/messages` | 챗봇 메시지 전송: 세션 분석 결과 기반 LLM 답변 생성 |

아래 예시는 모두 같은 체험자 기준입니다: `age=21, heightCm=174, weightKg=63, hairThickness=THIN, hairCurl=SEMI_CURLY, hairDensity=MEDIUM`, 얼굴형 `heart`(faceIndex 1), 체형 `small_inv_triangle`(bodyIndex 1) → `1 × 6 + 1 + 1 = 8` → `TYPE-08` (`recommendation-dataset.csv`의 S006과 동일).

---

### ① 게스트 세션 생성

`POST /api/v1/sessions/guest`

**Request Body** (`application/json`)

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `photoConsent` | boolean | 필수 | 반드시 `true` | 얼굴·전신 사진 분석 동의 (F-01-03, R-01) |
| `deviceLabel` | string | 선택 | 최대 50자 | 부스 기기 구분용 (예: `booth-01`), 없으면 `null` |

```json
{
  "photoConsent": true,
  "deviceLabel": "booth-01"
}
```

**Response `201 Created`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "createdAt": "2026-11-19T21:30:00+09:00",
    "expiresAt": "2026-11-19T22:00:00+09:00",
    "ttlMinutes": 30,
    "progress": { "profileInput": false, "faceAnalysis": false, "bodyAnalysis": false },
    "nextStep": "PROFILE_INPUT"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string (UUID) | 불가 | 이후 모든 API 경로에 사용. 앱 메모리(전역 상태)에 보관 |
| `createdAt` | string | 불가 | |
| `expiresAt` | string | 불가 | 만료 예정 시각 |
| `ttlMinutes` | int | 불가 | 30 |
| `progress` | `Progress` | 불가 | |
| `nextStep` | `NextStep` | 불가 | 항상 `PROFILE_INPUT` |

**에러**: `400 PHOTO_CONSENT_REQUIRED` (`photoConsent`가 `false`·누락), `400 INVALID_REQUEST`

---

### ② 기본 정보 입력

`POST /api/v1/sessions/{sessionId}/profile-input`

- 처음 입력과 수정 모두 이 API로 처리합니다(전체 덮어쓰기). 다시 호출해도 됩니다.
- 이미 체형 분석이 끝난 뒤 `heightCm`가 바뀌면, 체형 결과가 초기화됩니다(`bodyAnalysisReset: true`). 이 경우 전신 사진을 다시 분석해야 합니다.

**Request Body** (`application/json`)

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `age` | int | 필수 | 14 ~ 80 |
| `heightCm` | int | 필수 | 140 ~ 210 |
| `weightKg` | int \| null | 선택 | 30 ~ 200, 미입력 시 `null` 또는 키 생략 (패션 추천에서 BMI 보정만 생략) |
| `hairThickness` | `HairThickness` | 필수 | `THIN` / `MEDIUM` / `THICK` |
| `hairCurl` | `HairCurl` | 필수 | `STRAIGHT` / `SEMI_CURLY` / `CURLY` |
| `hairDensity` | `HairDensity` | 필수 | `LOW` / `MEDIUM` / `HIGH` |

```json
{
  "age": 21,
  "heightCm": 174,
  "weightKg": 63,
  "hairThickness": "THIN",
  "hairCurl": "SEMI_CURLY",
  "hairDensity": "MEDIUM"
}
```

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "profileInput": {
      "age": 21,
      "heightCm": 174,
      "weightKg": 63,
      "hairThickness": "THIN",
      "hairCurl": "SEMI_CURLY",
      "hairDensity": "MEDIUM"
    },
    "bodyAnalysisReset": false,
    "progress": { "profileInput": true, "faceAnalysis": false, "bodyAnalysis": false },
    "nextStep": "FACE_ANALYSIS",
    "expiresAt": "2026-11-19T22:01:10+09:00"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `profileInput` | `ProfileInput` | 불가 | 저장된 값 |
| `bodyAnalysisReset` | boolean | 불가 | 키 변경으로 체형 결과가 초기화되었으면 `true` |
| `progress` | `Progress` | 불가 | |
| `nextStep` | `NextStep` | 불가 | |
| `expiresAt` | string | 불가 | 연장된 만료 시각 |

**에러**: `400 INVALID_REQUEST`, `404 SESSION_NOT_FOUND`, `410 SESSION_EXPIRED`

검증 실패 예 (`age: 12`, `hairCurl: "WAVY"`):

```json
{
  "success": false,
  "error": {
    "status": 400,
    "code": "INVALID_REQUEST",
    "message": "입력값을 확인해 주세요.",
    "details": [
      { "field": "age", "reason": "14 이상 80 이하의 정수로 입력해 주세요.", "rejectedValue": 12 },
      { "field": "hairCurl", "reason": "STRAIGHT, SEMI_CURLY, CURLY 중 하나여야 합니다.", "rejectedValue": "WAVY" }
    ],
    "meta": null,
    "path": "/api/v1/sessions/3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01/profile-input",
    "timestamp": "2026-11-19T21:31:05+09:00"
  }
}
```

---

### ③ 얼굴 사진 분석

`POST /api/v1/sessions/{sessionId}/analyze/face`

- 실시간 카메라 미리보기·가이드 오버레이는 앱에서 처리하고, **촬영 버튼을 누른 시점의 정지 이미지 1장**을 전송합니다.
- 기본 정보 입력 전에도 호출할 수 있지만, 화면 흐름은 ② → ③ 순서를 권장합니다.
- 재호출(재촬영) 시 성공한 최신 결과로 교체되고 `source`는 `AI`가 됩니다. `usable: false`이면 이전 결과는 그대로 유지됩니다.
- **`usable: false`이면 `progress`·`nextStep`이 바뀌지 않으며, 앱은 경고창(`warnings[].message`)과 재촬영 안내만 띄우고 다음 화면으로 이동하지 않습니다.** 정상 분석(`usable: true`)이 완료되었을 때만 다음 단계로 진행합니다.
- 권장 클라이언트 타임아웃: 30초.

**Request** (`multipart/form-data`)

| 파트 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `image` | file | 필수 | `image/jpeg` 또는 `image/png`, 10MB 이하. 정면 얼굴 1명, 권장 해상도 짧은 변 720px 이상 |

**Response `200 OK` — 분석 성공 (`usable: true`)**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "analysisType": "FACE",
    "usable": true,
    "retakeRequired": false,
    "faceShape": { "key": "heart", "label": "하트형" },
    "confidence": 0.64,
    "borderline": false,
    "warnings": [],
    "progress": { "profileInput": true, "faceAnalysis": true, "bodyAnalysis": false },
    "nextStep": "BODY_ANALYSIS",
    "analyzedAt": "2026-11-19T21:32:20+09:00",
    "expiresAt": "2026-11-19T22:02:20+09:00"
  }
}
```

**Response `200 OK` — 분석 불가 (`usable: false`, 재촬영 필요)**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "analysisType": "FACE",
    "usable": false,
    "retakeRequired": true,
    "faceShape": null,
    "confidence": null,
    "borderline": null,
    "warnings": [
      { "code": "FACE_YAW_EXCEEDED", "severity": "RETAKE", "message": "고개를 좌우로 돌리지 말고 정면을 바라봐 주세요." },
      { "code": "SMILING", "severity": "RETAKE", "message": "무표정에 가까운 편안한 표정으로 촬영해 주세요." }
    ],
    "progress": { "profileInput": true, "faceAnalysis": false, "bodyAnalysis": false },
    "nextStep": "FACE_ANALYSIS",
    "analyzedAt": "2026-11-19T21:32:20+09:00",
    "expiresAt": "2026-11-19T22:02:20+09:00"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `analysisType` | string | 불가 | 항상 `FACE` |
| `usable` | boolean | 불가 | AI가 판정 가능한 사진이었는지 |
| `retakeRequired` | boolean | 불가 | `!usable`. `true`면 `warnings`의 `RETAKE` 메시지를 보여주고 재촬영 |
| `faceShape` | `CodeLabel` | 가능 | `usable=false`이면 `null` |
| `confidence` | number | 가능 | 1위 후보 확률(0~1), `usable=false`이면 `null` |
| `borderline` | boolean | 가능 | 1위 확률 < 0.60이면 `true` (경계 얼굴), `usable=false`이면 `null` |
| `warnings` | `Warning[]` | 불가 | 없으면 `[]`. `usable=true`여도 `INFO` 경고가 있을 수 있음 (예: `FACE_BORDERLINE`) |
| `progress` | `Progress` | 불가 | |
| `nextStep` | `NextStep` | 불가 | |
| `analyzedAt` | string | 불가 | |
| `expiresAt` | string | 불가 | |

**에러**: `400 IMAGE_REQUIRED`, `413 IMAGE_TOO_LARGE`, `415 UNSUPPORTED_IMAGE_TYPE`, `404 SESSION_NOT_FOUND`, `410 SESSION_EXPIRED`, `409 ANALYSIS_IN_PROGRESS`, `502 AI_BAD_RESPONSE`, `503 AI_SERVER_UNAVAILABLE`, `504 AI_TIMEOUT`

---

### ④ 전신 사진 분석

`POST /api/v1/sessions/{sessionId}/analyze/body`

- 체형 분석에는 키가 필수이므로 **세션에 저장된 `heightCm`를 서버가 자동으로 사용**합니다. 앱에서 키를 따로 보내지 않습니다.
- ②가 완료되지 않았으면 `409 PROFILE_INPUT_REQUIRED`.
- 권장 클라이언트 타임아웃: 30초.

**Request** (`multipart/form-data`)

| 파트 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `image` | file | 필수 | `image/jpeg` 또는 `image/png`, 10MB 이하. 머리~발끝 전체가 나온 정면 전신 1명, 몸에 붙는 옷 권장 |

**Response `200 OK` — 분석 성공**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "analysisType": "BODY",
    "usable": true,
    "retakeRequired": false,
    "bodyType": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
    "confidence": 0.68,
    "borderline": false,
    "heightCmUsed": 174,
    "warnings": [],
    "progress": { "profileInput": true, "faceAnalysis": true, "bodyAnalysis": true },
    "nextStep": "RESULT_READY",
    "analyzedAt": "2026-11-19T21:33:40+09:00",
    "expiresAt": "2026-11-19T22:03:40+09:00"
  }
}
```

**Response `200 OK` — 분석 불가**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "analysisType": "BODY",
    "usable": false,
    "retakeRequired": true,
    "bodyType": null,
    "confidence": null,
    "borderline": null,
    "heightCmUsed": 174,
    "warnings": [
      { "code": "BODY_LOW_VISIBILITY", "severity": "RETAKE", "message": "어깨·골반·무릎·발목이 잘 보이도록 촬영해 주세요." }
    ],
    "progress": { "profileInput": true, "faceAnalysis": true, "bodyAnalysis": false },
    "nextStep": "BODY_ANALYSIS",
    "analyzedAt": "2026-11-19T21:33:40+09:00",
    "expiresAt": "2026-11-19T22:03:40+09:00"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `analysisType` | string | 불가 | 항상 `BODY` |
| `bodyType` | `CodeLabel` | 가능 | `usable=false`이면 `null` |
| `heightCmUsed` | int | 불가 | 분석에 사용한 세션 키 |
| `confidence` | number | 가능 | `usable=false`이면 `null`. **`usable=true`여도 AI(체형 유료 API)가 제공하지 않으면 `null`** |
| `borderline` | boolean | 가능 | 위와 동일 |
| 나머지 | | | ③과 동일 (`usable`, `retakeRequired`, `warnings`, `progress`, `nextStep`, `analyzedAt`, `expiresAt`) |

> 앱은 `usable`, `bodyType`, `warnings`만으로 화면 흐름을 결정합니다. `confidence`·`borderline`은 값이 있을 때만 표시합니다.
> `usable: false` 처리는 ③과 동일합니다(재촬영 안내만, 다음 단계 이동 불가).

**에러**: `409 PROFILE_INPUT_REQUIRED` + ③의 에러 전체

---

### ⑤ 최종 결과 조회

`GET /api/v1/sessions/{sessionId}/result`

- 얼굴형·체형이 모두 확정(`progress.faceAnalysis && progress.bodyAnalysis`)되어야 합니다. 기본 정보(②)도 필요합니다.
- 결과는 호출 시점의 입력값으로 매번 계산되며, 같은 입력이면 항상 같은 결과입니다.
- 결과 화면 진입 시 1회 호출, [분석 상세보기]에서 보정(⑦) 후에는 ⑦의 응답으로 화면을 갱신하면 되므로 다시 호출할 필요가 없습니다.

**Request**: Body 없음

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "ruleVersion": "v1.1-draft",
    "generatedAt": "2026-11-19T21:33:45+09:00",
    "type": {
      "typeCode": "TYPE-08",
      "typeName": "V라인 프레시",
      "nameConfirmed": false,
      "headline": "당신은 V라인 프레시 유형입니다",
      "description": "또렷한 어깨선의 슬림한 체형과 시원한 이마에서 갸름하게 모이는 턱선이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다.",
      "stylePoints": [
        "테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조",
        "가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기"
      ]
    },
    "basis": {
      "faceShape": { "key": "heart", "label": "하트형" },
      "bodyType": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
      "faceSource": "AI",
      "bodySource": "AI"
    },
    "hairStyles": [
      {
        "rank": 1,
        "code": "PARTED_STYLE",
        "name": "가르마 스타일",
        "description": "2:8~5:5 가르마로 이마를 일부 드러내고 옆 볼륨을 살린 스타일 (가르마펌 등)",
        "imageKey": "hair_parted_style",
        "score": 6,
        "reasons": [
          "가르마로 이마를 사선으로 나눠, 시원한 이마 라인과 갸름한 턱선이 균형 있게 보입니다.",
          "자연스러운 웨이브가 가르마 라인에 입체감을 더해 줍니다."
        ],
        "reasonCodes": ["FACE_HEART_PARTED_STYLE", "ADJ_CURL_SEMI_CURLY_PARTED_STYLE"],
        "scoreBreakdown": {
          "base": 5,
          "adjustments": [
            { "code": "ADJ_CURL_SEMI_CURLY_PARTED_STYLE", "factor": "hairCurl", "value": "SEMI_CURLY", "delta": 1 }
          ]
        }
      },
      {
        "rank": 2,
        "code": "SHADOW_STYLE",
        "name": "쉐도우 스타일",
        "description": "앞머리를 내리고 C컬·S컬 웨이브로 볼륨을 준 스타일 (쉐도우펌 등)",
        "imageKey": "hair_shadow_style",
        "score": 6,
        "reasons": [
          "앞머리 웨이브가 이마를 부드럽게 덮고 턱 주변까지 자연스러운 볼륨 흐름을 만들어 줍니다.",
          "펌의 컬이 가는 모발에 자연스러운 볼륨을 더해 줍니다."
        ],
        "reasonCodes": ["FACE_HEART_SHADOW_STYLE", "ADJ_THICKNESS_THIN_SHADOW_STYLE"],
        "scoreBreakdown": {
          "base": 4,
          "adjustments": [
            { "code": "ADJ_THICKNESS_THIN_SHADOW_STYLE", "factor": "hairThickness", "value": "THIN", "delta": 1 },
            { "code": "ADJ_CURL_SEMI_CURLY_SHADOW_STYLE", "factor": "hairCurl", "value": "SEMI_CURLY", "delta": 1 }
          ]
        }
      }
    ],
    "fashionStyles": [
      {
        "rank": 1,
        "code": "DANDY",
        "name": "댄디",
        "description": "블레이저·셔츠·슬랙스 중심의 단정한 스타일",
        "imageKey": "fashion_dandy",
        "score": 5,
        "reasons": ["또렷한 어깨선과 슬림한 허리 라인이 테일러드 재킷의 실루엣을 돋보이게 합니다."],
        "reasonCodes": ["BODY_SMALL_INV_TRIANGLE_DANDY"],
        "scoreBreakdown": { "base": 5, "adjustments": [] },
        "itemKeywords": ["블레이저", "셔츠", "니트 폴로", "슬랙스", "로퍼"]
      },
      {
        "rank": 2,
        "code": "SPORTY",
        "name": "스포티",
        "description": "트랙·기능성 소재 중심의 활동적인 스타일",
        "imageKey": "fashion_sporty",
        "score": 5,
        "reasons": ["탄탄한 어깨 라인이 트랙 재킷과 기능성 웨어의 활동적인 핏을 살려 줍니다."],
        "reasonCodes": ["BODY_SMALL_INV_TRIANGLE_SPORTY"],
        "scoreBreakdown": { "base": 5, "adjustments": [] },
        "itemKeywords": ["트랙 재킷", "기능성 티셔츠", "조거 팬츠", "러닝화", "볼캡"]
      },
      {
        "rank": 3,
        "code": "CASUAL",
        "name": "캐주얼",
        "description": "셔츠·맨투맨·데님 등 기본 아이템 중심의 편안한 스타일",
        "imageKey": "fashion_casual",
        "score": 4,
        "reasons": ["슬림한 몸통과 또렷한 어깨선 덕분에 기본 셔츠·니트도 핏이 잘 삽니다."],
        "reasonCodes": ["BODY_SMALL_INV_TRIANGLE_CASUAL"],
        "scoreBreakdown": { "base": 4, "adjustments": [] },
        "itemKeywords": ["옥스퍼드 셔츠", "맨투맨", "치노 팬츠", "데님 팬츠", "스니커즈"]
      }
    ],
    "disclaimer": "분석 결과는 스타일 참고용이며, 언제든 [분석 상세보기]에서 직접 수정할 수 있어요."
  }
}
```

필드 정의는 3.11 `ResultData` 참고.

**에러**

| 상황 | 응답 |
|---|---|
| 얼굴·전신 중 미완료 항목 존재 | `409 ANALYSIS_INCOMPLETE` |
| 기본 정보 미입력 | `409 PROFILE_INPUT_REQUIRED` |
| 세션 없음 / 만료 | `404 SESSION_NOT_FOUND` / `410 SESSION_EXPIRED` |

`409 ANALYSIS_INCOMPLETE` 예:

```json
{
  "success": false,
  "error": {
    "status": 409,
    "code": "ANALYSIS_INCOMPLETE",
    "message": "얼굴과 전신 분석이 모두 필요해요.",
    "details": [],
    "meta": { "missing": ["BODY"], "nextStep": "BODY_ANALYSIS" },
    "path": "/api/v1/sessions/3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01/result",
    "timestamp": "2026-11-19T21:33:00+09:00"
  }
}
```

`meta.missing`: `"FACE"`, `"BODY"` 중 미완료 항목 배열.

---

### ⑥ 분석 상세보기

`GET /api/v1/sessions/{sessionId}/analysis-detail`

- 결과 화면의 **[분석 상세보기]** 버튼 → 팝업(모달)에서 사용합니다.
- 분석이 끝나지 않은 항목은 `status: "NOT_ANALYZED"`로 채워서 **항상 200**을 반환합니다.
- `editableOptions`는 팝업 안 “결과 수정” 선택지(드롭다운·칩)에 그대로 사용합니다.

**Request**: Body 없음

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "face": {
      "status": "ANALYZED",
      "source": "AI",
      "result": { "key": "heart", "label": "하트형" },
      "aiResult": { "key": "heart", "label": "하트형" },
      "description": "이마 라인이 시원하고 턱선이 갸름하게 모여 산뜻한 인상을 주는 얼굴형입니다.",
      "aiReason": "평균 대비 이마 너비 비율이 높고 턱끝 너비 비율이 낮아, 이마에서 턱끝으로 갸름하게 모이는 하트형 특징이 보입니다.",
      "confidence": 0.64,
      "borderline": false,
      "candidates": [
        { "key": "heart", "label": "하트형", "probability": 0.64 },
        { "key": "diamond", "label": "다이아몬드형", "probability": 0.21 },
        { "key": "oval", "label": "계란형", "probability": 0.09 },
        { "key": "round", "label": "둥근형", "probability": 0.03 },
        { "key": "long", "label": "긴형", "probability": 0.02 },
        { "key": "rectangle", "label": "직사각형", "probability": 0.01 }
      ],
      "warnings": [],
      "features": [
        { "key": "length_ratio", "label": "얼굴 길이 비율", "value": 1.18, "unit": "ratio", "description": "얼굴 길이 ÷ 광대 폭" },
        { "key": "forehead_ratio", "label": "이마 너비 비율", "value": 0.93, "unit": "ratio", "description": "이마 폭 ÷ 광대 폭" },
        { "key": "jaw_ratio", "label": "턱 너비 비율", "value": 0.69, "unit": "ratio", "description": "턱 폭 ÷ 광대 폭" },
        { "key": "chin_ratio", "label": "턱끝 너비 비율", "value": 0.62, "unit": "ratio", "description": "턱끝 폭 ÷ 광대 폭" },
        { "key": "jaw_angle_deg", "label": "턱 각도", "value": 141.5, "unit": "deg", "description": "턱 윤곽선이 이루는 각도" }
      ],
      "analyzedAt": "2026-11-19T21:32:20+09:00"
    },
    "body": {
      "status": "ANALYZED",
      "source": "AI",
      "result": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
      "aiResult": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
      "description": "몸통이 슬림하고 어깨선이 상대적으로 또렷해 V라인 실루엣이 살아나는 체형입니다.",
      "confidence": 0.68,
      "borderline": false,
      "candidates": [
        { "key": "small_inv_triangle", "label": "작은 역삼각형", "probability": 0.68 },
        { "key": "standard", "label": "표준유형", "probability": 0.22 },
        { "key": "rectangle", "label": "사각 체형", "probability": 0.07 },
        { "key": "large_rectangle", "label": "큰 사각 체형", "probability": 0.03 }
      ],
      "warnings": [],
      "features": [
        { "key": "shoulder_dev", "label": "어깨 편차", "value": 4.0, "unit": "percent", "description": "키 대비 어깨 너비의 평균 대비 차이" },
        { "key": "trunk_dev", "label": "몸통 편차", "value": -3.1, "unit": "percent", "description": "키 대비 허리·엉덩이 너비의 평균 대비 차이" },
        { "key": "shoulder_minus_trunk", "label": "어깨-몸통 편차 차이", "value": 7.1, "unit": "percent", "description": "어깨 편차 − 몸통 편차" }
      ],
      "heightCmUsed": 174,
      "compareToSizeKorea": {
        "reference": "SIZE_KOREA_20S_MALE",
        "referenceLabel": "Size Korea 20대 남성 평균",
        "heightCm": 174,
        "items": [
          { "key": "bideltoid_width", "label": "어깨 너비", "userMm": 491, "refMm": 470, "deviation": 0.040, "deviationPercent": 4.0, "direction": "ABOVE" },
          { "key": "waist_width", "label": "허리 너비", "userMm": 261, "refMm": 271, "deviation": -0.041, "deviationPercent": -4.1, "direction": "BELOW" },
          { "key": "hip_width", "label": "엉덩이 너비", "userMm": 325, "refMm": 330, "deviation": -0.020, "deviationPercent": -2.0, "direction": "BELOW" },
          { "key": "shoulder_height", "label": "어깨 높이", "userMm": 1420, "refMm": 1399, "deviation": 0.010, "deviationPercent": 1.0, "direction": "SIMILAR" },
          { "key": "crotch_height", "label": "다리 길이(샅높이)", "userMm": 814, "refMm": 794, "deviation": 0.020, "deviationPercent": 2.0, "direction": "ABOVE" },
          { "key": "arm_length", "label": "팔 길이", "userMm": 584, "refMm": 581, "deviation": 0.001, "deviationPercent": 0.1, "direction": "SIMILAR" }
        ],
        "summary": { "shoulderDeviation": 0.040, "trunkDeviation": -0.031 }
      },
      "analyzedAt": "2026-11-19T21:33:40+09:00"
    },
    "profileInput": {
      "age": 21,
      "heightCm": 174,
      "weightKg": 63,
      "hairThickness": "THIN",
      "hairCurl": "SEMI_CURLY",
      "hairDensity": "MEDIUM"
    },
    "editableOptions": {
      "faceShapes": [
        { "key": "diamond", "label": "다이아몬드형" },
        { "key": "heart", "label": "하트형" },
        { "key": "oval", "label": "계란형" },
        { "key": "rectangle", "label": "직사각형" },
        { "key": "round", "label": "둥근형" },
        { "key": "long", "label": "긴형" }
      ],
      "bodyTypes": [
        { "key": "standard", "label": "표준유형" },
        { "key": "small_inv_triangle", "label": "작은 역삼각형" },
        { "key": "large_rectangle", "label": "큰 사각 체형" },
        { "key": "rectangle", "label": "사각 체형" }
      ]
    }
  }
}
```

**`data` 필드**

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `face` | `FaceDetail` | 불가 | 미분석이어도 객체는 존재 (`status: NOT_ANALYZED`) |
| `body` | `BodyDetail` | 불가 | 동일 |
| `profileInput` | `ProfileInput` | 가능 | ② 미완료 시 `null` |
| `editableOptions.faceShapes` | `CodeLabel[]` | 불가 | 6개, 고정 순서 |
| `editableOptions.bodyTypes` | `CodeLabel[]` | 불가 | 4개, 고정 순서 |

**`FaceDetail`**

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `status` | `AnalysisStatus` | 불가 | `ANALYZED` / `CORRECTED` / `MANUAL` / `NOT_ANALYZED` |
| `source` | `AnalysisSource` | 가능 | 현재 적용 중인 값의 출처. `NOT_ANALYZED`면 `null` |
| `result` | `CodeLabel` | 가능 | 현재 적용 중인 얼굴형 (추천 계산에 쓰이는 값) |
| `aiResult` | `CodeLabel` | 가능 | AI 원본 판정. AI 분석 이력이 없으면 `null` |
| `description` | string | 가능 | `result` 기준 얼굴형 설명 (`style-rules.md` 6.1) |
| `aiReason` | string | 가능 | **(v1.2, AI `v8`)** AI가 제공한 평균 대비 특징 요약 문장(부록 A.2 `reason`). AI 이력이 없거나 AI가 제공하지 않으면 `null`. `aiResult` 기준 문장이므로 `status=CORRECTED`이면 현재 `result`와 다를 수 있어, 화면에는 `status=ANALYZED`일 때만 표시 권장 |
| `confidence` | number | 가능 | AI 1위 확률. AI 이력이 없거나 AI가 제공하지 않으면 `null` |
| `borderline` | boolean | 가능 | AI 이력이 없거나 AI가 제공하지 않으면 `null` |
| `candidates` | `Candidate[]` | 불가 | AI 후보, 확률 내림차순. AI 이력이 없거나 AI가 제공하지 않으면 `[]` |
| `warnings` | `Warning[]` | 불가 | 마지막 성공 분석의 경고(주로 `INFO`), 없으면 `[]` |
| `features` | `Feature[]` | 불가 | AI 측정값. AI 이력이 없거나 AI가 제공하지 않으면 `[]` |
| `analyzedAt` | string | 가능 | 마지막 AI 분석 성공 시각 |

**`BodyDetail`** = `FaceDetail`의 모든 필드(`description`은 6.2 체형 설명, **`aiReason` 제외** — 얼굴 전용) + 아래 필드

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `heightCmUsed` | int | 가능 | AI 분석에 사용한 키, AI 이력이 없으면 `null` |
| `compareToSizeKorea` | `SizeKoreaCompare` | 가능 | AI 이력이 없거나 AI가 제공하지 않으면 `null` |

> 얼굴 쪽에는 `compareToSizeKorea`가 없습니다(체형 전용). 반대로 `aiReason`은 얼굴 전용입니다.

**체형 부가 정보 선택 규칙 (유료 API 미확정 대응)**

- 체형 분석은 `status: "ANALYZED"`여도 `confidence`·`borderline`이 `null`, `candidates`·`features`가 `[]`, `compareToSizeKorea`가 `null`일 수 있습니다. 이는 **정상 응답**이며 에러가 아닙니다.
- 프론트엔드는 **값이 존재하는 항목만 화면에 표시**합니다. `null`이면 해당 행·섹션을 숨기고, 배열이 `[]`이면 해당 리스트를 렌더링하지 않습니다. 이때 “데이터 없음” 같은 빈 상태 문구도 표시하지 않습니다.
- 항상 표시 가능한 최소 정보: `status`, `result`(판정 체형), `description`.

체형 부가 정보가 없는 경우의 `body` 예 (유료 API가 필수 필드만 반환):

```json
{
  "body": {
    "status": "ANALYZED",
    "source": "AI",
    "result": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
    "aiResult": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
    "description": "몸통이 슬림하고 어깨선이 상대적으로 또렷해 V라인 실루엣이 살아나는 체형입니다.",
    "confidence": null,
    "borderline": null,
    "candidates": [],
    "warnings": [],
    "features": [],
    "heightCmUsed": 174,
    "compareToSizeKorea": null,
    "analyzedAt": "2026-11-19T21:33:40+09:00"
  }
}
```

`status` 판정 규칙

| status | 조건 |
|---|---|
| `ANALYZED` | `source=AI` |
| `CORRECTED` | `source=MANUAL` 이고 `aiResult != null` |
| `MANUAL` | `source=MANUAL` 이고 `aiResult == null` |
| `NOT_ANALYZED` | 결과 없음 (`source`, `result`, `aiResult` 모두 `null`) |

미분석 항목 예 (`body`):

```json
"body": {
  "status": "NOT_ANALYZED",
  "source": null,
  "result": null,
  "aiResult": null,
  "description": null,
  "confidence": null,
  "borderline": null,
  "candidates": [],
  "warnings": [],
  "features": [],
  "heightCmUsed": null,
  "compareToSizeKorea": null,
  "analyzedAt": null
}
```

**에러**: `404 SESSION_NOT_FOUND`, `410 SESSION_EXPIRED`

---

### ⑦ 수동 보정 및 결과 재산출

`PATCH /api/v1/sessions/{sessionId}/profile`

- [분석 상세보기] 팝업에서 AI 판정을 직접 고칠 때 사용합니다. 또한 **AI 서버 장애(`503 AI_SERVER_UNAVAILABLE` / `504 AI_TIMEOUT`) 시** 노출하는 “직접 선택하기”에도 사용합니다(AI 결과 없이도 설정 가능).
- 분석 불가 사진(`usable: false`)에 대해서는 이 API로 우회하지 않고 재촬영을 안내합니다.
- **보낸 필드만 변경**합니다. 키를 생략하거나 `null`이면 변경하지 않습니다. 최소 1개 필드는 `null`이 아니어야 합니다.
- 나이·키·몸무게는 이 API로 바꾸지 않습니다(② 재호출).

**Request Body** (`application/json`)

| 필드 | 타입 | 필수 | 검증 | 효과 |
|---|---|---|---|---|
| `faceShape` | `FaceShapeKey` \| null | 선택 | 6종 Key | 얼굴형 `source=MANUAL` → 유형·헤어 재산출 |
| `bodyType` | `BodyTypeKey` \| null | 선택 | 4종 Key | 체형 `source=MANUAL` → 유형·패션 재산출 |
| `hairThickness` | `HairThickness` \| null | 선택 | | 헤어 재산출 (② 미완료 시 `409 PROFILE_INPUT_REQUIRED`) |
| `hairCurl` | `HairCurl` \| null | 선택 | | 헤어 재산출 (동일) |
| `hairDensity` | `HairDensity` \| null | 선택 | | 헤어 재산출 (동일) |

```json
{
  "faceShape": "diamond"
}
```

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "changedFields": ["faceShape"],
    "progress": { "profileInput": true, "faceAnalysis": true, "bodyAnalysis": true },
    "nextStep": "RESULT_READY",
    "expiresAt": "2026-11-19T22:05:10+09:00",
    "result": {
      "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
      "ruleVersion": "v1.1-draft",
      "generatedAt": "2026-11-19T21:35:10+09:00",
      "type": {
        "typeCode": "TYPE-07",
        "typeName": "V라인 샤프",
        "nameConfirmed": false,
        "headline": "당신은 V라인 샤프 유형입니다",
        "description": "또렷한 어깨선의 슬림한 체형과 입체적인 광대 라인이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다.",
        "stylePoints": [
          "테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조",
          "옆 볼륨이 있는 가르마 헤어로 관자놀이 라인 채우기"
        ]
      },
      "basis": {
        "faceShape": { "key": "diamond", "label": "다이아몬드형" },
        "bodyType": { "key": "small_inv_triangle", "label": "작은 역삼각형" },
        "faceSource": "MANUAL",
        "bodySource": "AI"
      },
      "hairStyles": [
        {
          "rank": 1,
          "code": "PARTED_STYLE",
          "name": "가르마 스타일",
          "description": "2:8~5:5 가르마로 이마를 일부 드러내고 옆 볼륨을 살린 스타일 (가르마펌 등)",
          "imageKey": "hair_parted_style",
          "score": 6,
          "reasons": [
            "가르마로 생기는 옆 볼륨이 관자놀이 라인을 채워, 또렷한 광대 라인과 조화로운 균형을 만듭니다.",
            "자연스러운 웨이브가 가르마 라인에 입체감을 더해 줍니다."
          ],
          "reasonCodes": ["FACE_DIAMOND_PARTED_STYLE", "ADJ_CURL_SEMI_CURLY_PARTED_STYLE"],
          "scoreBreakdown": {
            "base": 5,
            "adjustments": [
              { "code": "ADJ_CURL_SEMI_CURLY_PARTED_STYLE", "factor": "hairCurl", "value": "SEMI_CURLY", "delta": 1 }
            ]
          }
        },
        {
          "rank": 2,
          "code": "SHADOW_STYLE",
          "name": "쉐도우 스타일",
          "description": "앞머리를 내리고 C컬·S컬 웨이브로 볼륨을 준 스타일 (쉐도우펌 등)",
          "imageKey": "hair_shadow_style",
          "score": 6,
          "reasons": [
            "웨이브 볼륨이 이마와 옆머리를 채워 입체적인 얼굴 윤곽을 부드럽게 이어 줍니다.",
            "펌의 컬이 가는 모발에 자연스러운 볼륨을 더해 줍니다."
          ],
          "reasonCodes": ["FACE_DIAMOND_SHADOW_STYLE", "ADJ_THICKNESS_THIN_SHADOW_STYLE"],
          "scoreBreakdown": {
            "base": 4,
            "adjustments": [
              { "code": "ADJ_THICKNESS_THIN_SHADOW_STYLE", "factor": "hairThickness", "value": "THIN", "delta": 1 },
              { "code": "ADJ_CURL_SEMI_CURLY_SHADOW_STYLE", "factor": "hairCurl", "value": "SEMI_CURLY", "delta": 1 }
            ]
          }
        }
      ],
      "fashionStyles": [
        {
          "rank": 1,
          "code": "DANDY",
          "name": "댄디",
          "description": "블레이저·셔츠·슬랙스 중심의 단정한 스타일",
          "imageKey": "fashion_dandy",
          "score": 5,
          "reasons": ["또렷한 어깨선과 슬림한 허리 라인이 테일러드 재킷의 실루엣을 돋보이게 합니다."],
          "reasonCodes": ["BODY_SMALL_INV_TRIANGLE_DANDY"],
          "scoreBreakdown": { "base": 5, "adjustments": [] },
          "itemKeywords": ["블레이저", "셔츠", "니트 폴로", "슬랙스", "로퍼"]
        },
        {
          "rank": 2,
          "code": "SPORTY",
          "name": "스포티",
          "description": "트랙·기능성 소재 중심의 활동적인 스타일",
          "imageKey": "fashion_sporty",
          "score": 5,
          "reasons": ["탄탄한 어깨 라인이 트랙 재킷과 기능성 웨어의 활동적인 핏을 살려 줍니다."],
          "reasonCodes": ["BODY_SMALL_INV_TRIANGLE_SPORTY"],
          "scoreBreakdown": { "base": 5, "adjustments": [] },
          "itemKeywords": ["트랙 재킷", "기능성 티셔츠", "조거 팬츠", "러닝화", "볼캡"]
        },
        {
          "rank": 3,
          "code": "CASUAL",
          "name": "캐주얼",
          "description": "셔츠·맨투맨·데님 등 기본 아이템 중심의 편안한 스타일",
          "imageKey": "fashion_casual",
          "score": 4,
          "reasons": ["슬림한 몸통과 또렷한 어깨선 덕분에 기본 셔츠·니트도 핏이 잘 삽니다."],
          "reasonCodes": ["BODY_SMALL_INV_TRIANGLE_CASUAL"],
          "scoreBreakdown": { "base": 4, "adjustments": [] },
          "itemKeywords": ["옥스퍼드 셔츠", "맨투맨", "치노 팬츠", "데님 팬츠", "스니커즈"]
        }
      ],
      "disclaimer": "분석 결과는 스타일 참고용이며, 언제든 [분석 상세보기]에서 직접 수정할 수 있어요."
    }
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `changedFields` | string[] | 불가 | 실제로 값이 바뀐 필드명. 같은 값을 보내면 `[]` |
| `progress` | `Progress` | 불가 | |
| `nextStep` | `NextStep` | 불가 | |
| `expiresAt` | string | 불가 | |
| `result` | `ResultData` | 가능 | 결과 산출 가능하면 ⑤와 동일 구조, 아직 불가능하면(예: 체형 미확정) `null` |

> 이 예시에서 얼굴형이 `heart`(faceIndex 1) → `diamond`(faceIndex 0)로 바뀌어 `typeCode`가 `TYPE-08` → `TYPE-07`로 재산출되었고, 헤어 추천 이유도 다이아몬드형 기준 문구로 바뀌었습니다. 헤어 순위는 두 얼굴형의 해당 기본 점수가 같아 동일하고, 체형이 그대로이므로 패션 추천은 변하지 않습니다.

**에러**: `400 INVALID_REQUEST` (모든 필드가 `null`/누락, 잘못된 Enum), `409 PROFILE_INPUT_REQUIRED`, `404 SESSION_NOT_FOUND`, `410 SESSION_EXPIRED`

모든 필드 누락 시:

```json
{
  "success": false,
  "error": {
    "status": 400,
    "code": "INVALID_REQUEST",
    "message": "수정할 항목을 1개 이상 보내 주세요.",
    "details": [],
    "meta": null,
    "path": "/api/v1/sessions/3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01/profile",
    "timestamp": "2026-11-19T21:35:00+09:00"
  }
}
```

---

### ⑧ 세션 초기화 (부스 시연용)

`DELETE /api/v1/sessions/{sessionId}`

- 세션, 기본 정보, 분석 결과, **챗봇 대화 내역(`chat_message`)**, 서버에 남아 있을 수 있는 임시 사진 파일을 **즉시 파기**합니다. (R-01) 응답 구조는 변경 없습니다.
- **멱등(idempotent)**: 이미 삭제·만료되었거나 존재하지 않는 세션이어도 `200`을 반환합니다(`alreadyDeleted: true`). 프론트는 응답과 관계없이 로컬 상태를 지우고 시작 화면으로 이동하면 됩니다.

**Request**: Body 없음

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "deleted": true,
    "alreadyDeleted": false,
    "deletedPhotoCount": 0,
    "deletedAt": "2026-11-19T21:40:00+09:00"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `deleted` | boolean | 불가 | 항상 `true` (요청 후 세션이 존재하지 않는 상태) |
| `alreadyDeleted` | boolean | 불가 | 호출 전에 이미 없던 세션이면 `true` |
| `deletedPhotoCount` | int | 불가 | 이번 호출로 삭제한 임시 사진 수. 분석 직후 사진은 이미 삭제되므로 보통 0 (분석 도중 초기화 시 1 이상) |
| `deletedAt` | string | 불가 | |

**에러**: `500 INTERNAL_ERROR`만 해당

---

### 챗봇 공통 규칙 (⑨·⑩)

| 항목 | 규칙 |
|---|---|
| 선행 조건 | ⑤ `GET /result`와 동일: 기본 정보(②) 입력 + 얼굴형·체형 모두 확정(`progress.faceAnalysis && progress.bodyAnalysis`). 미완료 시 `409 PROFILE_INPUT_REQUIRED` / `409 ANALYSIS_INCOMPLETE` (`meta.missing`, `meta.nextStep`은 ⑤와 동일) |
| 턴 정의 | **1턴 = 사용자 메시지 1개 + 챗봇 답변 1개가 모두 저장된 성공 쌍**. 세션당 최대 20턴(`moda.chat.max-turns`) |
| 남은 턴 | `remainingTurns = maxTurns − 저장된 턴 수` (0 ~ 20). LLM 호출 실패(`503`·`504`) 시 사용자 메시지도 저장하지 않으며 턴이 차감되지 않습니다 |
| 컨텍스트 | 매 호출마다 **현재 세션 상태**(`profileInput` + `faceDetail` + `bodyDetail` + `resultData`)로 시스템 프롬프트를 새로 구성합니다. ⑦ `PATCH /profile`로 결과를 수정하면 이후 첫 인사·답변에 자동 반영됩니다 |
| 표현 가이드 | 시스템 프롬프트에 `style-rules.md` 7장 표현 가이드(R-11)를 포함합니다. **몸무게·BMI 수치와 구간명은 답변에 노출하지 않도록** 지시하며, 패션 보정 근거로만 내부 참고합니다 |
| 개인정보 | 사진·이미지·랜드마크 좌표는 LLM에 전달하지 않습니다. 대화 내역은 세션 수명 동안만 보관 후 삭제(1.4) |
| 메시지 형식 | `content`는 일반 텍스트(줄바꿈 `\n` 포함 가능). 마크다운은 사용하지 않도록 시스템 프롬프트로 지시합니다 |
| 동시 전송 | 프론트는 ⑩ 응답 대기 중 전송 버튼·추천 질문 칩을 비활성화합니다. 서버는 세션 단위로 순차 처리하며 턴 한도를 원자적으로 검사합니다 |

`ChatMessage`

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `messageId` | string (UUID) | 불가 | 메시지 식별자 |
| `role` | `ChatRole` | 불가 | `USER` 또는 `ASSISTANT` |
| `content` | string | 불가 | 메시지 본문 |
| `createdAt` | string | 불가 | 저장 시각 |

---

### ⑨ 챗봇 대화 조회 (화면 진입)

`GET /api/v1/sessions/{sessionId}/chat/messages`

- 결과 화면의 **[챗봇 이용하기]** → 챗봇 화면 진입 시 1회 호출합니다. 화면 재진입 시에도 호출해 이전 대화를 복원합니다.
- `welcomeMessage`는 세션의 맞춤 유형(`typeName`)·얼굴형·체형·추천 스타일을 바탕으로 **서버 템플릿으로 생성**합니다(LLM 호출 없음). DB에 저장하지 않으며 `messages[]`에 포함되지 않고 턴으로 세지 않습니다. 화면에는 항상 대화 목록 맨 위 첫 말풍선으로 표시합니다.
- 조회 API이므로 세션 TTL을 연장하지 않습니다.

**Request**: Body 없음

**Response `200 OK`** — 첫 진입(대화 없음) 예, ④ 직후 `TYPE-08` 기준

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "welcomeMessage": "안녕하세요! Moda 스타일 챗봇이에요. 분석 결과 'V라인 프레시' 유형(하트형 · 작은 역삼각형)으로 나왔어요. 추천받은 가르마 스타일이나 댄디 룩 연출법, 상황별 코디가 궁금하면 편하게 물어보세요.",
    "messages": [],
    "suggestedPrompts": [
      "가르마 스타일은 어떻게 연출하면 좋을까요?",
      "댄디 룩 기본 아이템 조합을 알려 줘",
      "하트형 얼굴에 어울리는 안경테는?",
      "소개팅 코디를 추천해 줘"
    ],
    "remainingTurns": 20,
    "maxTurns": 20,
    "expiresAt": "2026-11-19T22:03:40+09:00"
  }
}
```

재진입(대화 1턴 진행 후) 시 `messages` 예:

```json
"messages": [
  { "messageId": "b1d2c3e4-0000-4a1b-9c2d-111111111111", "role": "USER", "content": "가르마 스타일은 어떻게 연출하면 좋을까요?", "createdAt": "2026-11-19T21:34:20+09:00" },
  { "messageId": "b1d2c3e4-0000-4a1b-9c2d-222222222222", "role": "ASSISTANT", "content": "하트형 얼굴은 이마 라인이 시원하고 턱선이 갸름하게 모여서, 6:4 정도의 자연스러운 가르마가 잘 어울려요. ...", "createdAt": "2026-11-19T21:34:23+09:00" }
],
"remainingTurns": 19
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `welcomeMessage` | string | 불가 | 세션 유형·얼굴형·체형 기반 맞춤 첫 인사 (저장·턴 차감 없음) |
| `messages` | `ChatMessage[]` | 불가 | 저장된 대화 내역, `createdAt` 오름차순(오래된 것 → 최신). 없으면 `[]`. 최대 40개(20턴) |
| `suggestedPrompts` | string[] | 불가 | 추천 질문 칩 3~4개. 첫 진입은 결과 기반, 대화가 있으면 마지막 답변 기준 후속 질문. `remainingTurns = 0`이면 `[]` |
| `remainingTurns` | int | 불가 | 남은 대화 턴 수 (0 ~ 20) |
| `maxTurns` | int | 불가 | 세션당 최대 턴 수 (20) |
| `expiresAt` | string | 불가 | 현재 세션 만료 예정 시각 (연장되지 않음) |

**에러**: `409 PROFILE_INPUT_REQUIRED`, `409 ANALYSIS_INCOMPLETE`, `404 SESSION_NOT_FOUND`, `410 SESSION_EXPIRED`

---

### ⑩ 챗봇 메시지 전송

`POST /api/v1/sessions/{sessionId}/chat/messages`

- 사용자가 입력창에 직접 입력하거나 추천 질문 칩을 탭하면 호출합니다(칩 탭 시 칩 문구를 `message`로 그대로 전송).
- 권장 클라이언트 타임아웃: 30초.

**Request Body** (`application/json`)

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `message` | string | 필수 | 앞뒤 공백 제거(trim) 후 **1 ~ 500자**. 공백·줄바꿈만으로 된 문자열 불가. 서버는 trim한 값을 저장 |

```json
{
  "message": "가르마 스타일은 어떻게 연출하면 좋을까요?"
}
```

**서버 처리 순서**

1. 요청 검증 → `400 INVALID_REQUEST`
2. 세션 확인 → `404 SESSION_NOT_FOUND` / `410 SESSION_EXPIRED`
3. 선행 조건 확인 → `409 PROFILE_INPUT_REQUIRED` / `409 ANALYSIS_INCOMPLETE`
4. 턴 한도 확인 (저장된 턴 수 ≥ 20) → `429 CHAT_TURN_LIMIT_EXCEEDED`
5. 시스템 프롬프트 구성: 세션의 `profileInput` + `faceDetail`(`aiReason` 포함) + `bodyDetail` + `resultData`(맞춤 유형·추천 헤어 2·패션 3·추천 이유)를 **백엔드가 자동 주입** + 표현 가이드(R-11). 이전 대화 내역(최대 19턴)을 함께 전달
6. LLM 호출 (`moda.chat.mode=external`: Spring AI `ChatClient` / `mock`: 템플릿 답변)
7. 성공 시 사용자 메시지·챗봇 답변을 **한 트랜잭션으로 `chat_message`에 저장**하고 세션 TTL 30분 연장
8. 응답 반환 (LLM 실패 시 아무것도 저장하지 않고 TTL도 연장하지 않음)

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "sessionId": "3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01",
    "userMessage": {
      "messageId": "b1d2c3e4-0000-4a1b-9c2d-111111111111",
      "role": "USER",
      "content": "가르마 스타일은 어떻게 연출하면 좋을까요?",
      "createdAt": "2026-11-19T21:34:20+09:00"
    },
    "assistantMessage": {
      "messageId": "b1d2c3e4-0000-4a1b-9c2d-222222222222",
      "role": "ASSISTANT",
      "content": "하트형 얼굴은 이마 라인이 시원하고 턱선이 갸름하게 모여서, 6:4 정도의 자연스러운 가르마가 잘 어울려요. 반곱슬 모발이라 드라이로 뿌리 볼륨만 살려도 옆 라인에 입체감이 생겨요.\n1) 젖은 상태에서 가르마 반대 방향으로 드라이해 뿌리 볼륨 만들기\n2) 가는 모발이므로 가벼운 텍스처 왁스를 소량만 사용하기\n3) 앞머리는 이마를 사선으로 살짝 덮도록 정리하기",
      "createdAt": "2026-11-19T21:34:23+09:00"
    },
    "suggestedPrompts": [
      "가르마펌을 하면 얼마나 유지되나요?",
      "가르마 스타일에 어울리는 코디는?",
      "왁스 대신 쓸 수 있는 제품은?"
    ],
    "remainingTurns": 19,
    "maxTurns": 20,
    "expiresAt": "2026-11-19T22:04:23+09:00"
  }
}
```

| 필드 | 타입 | null | 설명 |
|---|---|---|---|
| `sessionId` | string | 불가 | |
| `userMessage` | `ChatMessage` | 불가 | 저장된 사용자 메시지 (`role: USER`, trim 적용 값) |
| `assistantMessage` | `ChatMessage` | 불가 | 저장된 챗봇 답변 (`role: ASSISTANT`) |
| `suggestedPrompts` | string[] | 불가 | 이번 답변 기준 후속 추천 질문 3~4개. `remainingTurns = 0`이면 `[]` |
| `remainingTurns` | int | 불가 | 이번 턴 차감 후 남은 턴 수 |
| `maxTurns` | int | 불가 | 20 |
| `expiresAt` | string | 불가 | 연장된 만료 시각 |

**에러**

| 상황 | 응답 |
|---|---|
| `message` 누락·공백만·500자 초과 | `400 INVALID_REQUEST` (`details[].field = "message"`) |
| 세션 없음 / 만료 | `404 SESSION_NOT_FOUND` / `410 SESSION_EXPIRED` |
| 기본 정보 미입력 / 얼굴·전신 미완료 | `409 PROFILE_INPUT_REQUIRED` / `409 ANALYSIS_INCOMPLETE` |
| 세션당 최대 20턴 초과 | `429 CHAT_TURN_LIMIT_EXCEEDED` |
| LLM 연결 실패, LLM `5xx`, LLM 제공사 요청 한도·인증 오류, 빈 답변 | `503 CHAT_SERVER_UNAVAILABLE` |
| LLM 응답 시간 초과 (`moda.chat.timeout` 20초) | `504 CHAT_TIMEOUT` |

> LLM 제공사의 요청 한도 초과(제공사 측 `429`)는 앱에 `429`로 전달하지 않고 `503 CHAT_SERVER_UNAVAILABLE`로 변환합니다. 앱의 `429`는 **세션 턴 한도 초과 전용**입니다.

`429 CHAT_TURN_LIMIT_EXCEEDED` 예:

```json
{
  "success": false,
  "error": {
    "status": 429,
    "code": "CHAT_TURN_LIMIT_EXCEEDED",
    "message": "이번 체험에서 대화할 수 있는 횟수(20회)를 모두 사용했어요.",
    "details": [],
    "meta": { "maxTurns": 20, "usedTurns": 20, "remainingTurns": 0 },
    "path": "/api/v1/sessions/3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01/chat/messages",
    "timestamp": "2026-11-19T21:50:00+09:00"
  }
}
```

검증 실패 예 (`message: "   "`):

```json
{
  "success": false,
  "error": {
    "status": 400,
    "code": "INVALID_REQUEST",
    "message": "입력값을 확인해 주세요.",
    "details": [
      { "field": "message", "reason": "공백을 제외하고 1자 이상 500자 이하로 입력해 주세요.", "rejectedValue": "   " }
    ],
    "meta": null,
    "path": "/api/v1/sessions/3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01/chat/messages",
    "timestamp": "2026-11-19T21:34:10+09:00"
  }
}
```

---

## 6. 화면 ↔ API 1:1 매핑 (프론트엔드용)

| # | 화면 | 사용자 액션 | 호출 API | 성공 시 처리 | 실패·분기 처리 |
|---|---|---|---|---|---|
| 1 | 시작(앱 진입) | 사진 분석 동의 체크 + [시작하기] | ① `POST /sessions/guest` | `sessionId`를 전역 상태에 저장 → 기본 정보 화면 | `PHOTO_CONSENT_REQUIRED` → 동의 안내 |
| 2 | 기본 정보 입력 | 나이·키·몸무게(선택)·모질 3항목 입력 후 [다음] | ② `POST /profile-input` | `nextStep`에 따라 이동 (보통 `FACE_ANALYSIS`) | `INVALID_REQUEST` → `details`로 필드별 오류 표시 |
| 3 | 얼굴 촬영 (실시간 카메라 + 가이드 오버레이) | [촬영] | ③ `POST /analyze/face` | `usable=true` → (`INFO` 경고가 있으면 토스트) → 전신 사진 화면 | `usable=false` → `warnings[].message` 경고창 + 재촬영 안내 (다음 화면 이동 불가, 횟수 제한 없음) / `503`·`504` → 재시도 + [직접 선택하기] 노출 → ⑦ `PATCH {faceShape}` |
| 4 | 전신 사진 입력 | [촬영] 또는 [앨범에서 선택] | ④ `POST /analyze/body` | `usable=true` → 분석 중(로딩) 화면 → 결과 화면 | `usable=false` → `warnings[].message` 경고창 + 재촬영 안내 (다음 화면 이동 불가, 횟수 제한 없음) / `PROFILE_INPUT_REQUIRED` → 기본 정보 화면 / `503`·`504` → 재시도 + [직접 선택하기] 노출 → ⑦ `PATCH {bodyType}` |
| 5 | 분석 중(로딩) | 자동 진행 | (③·④ 응답 대기) | — | 타임아웃 30초 → `AI_TIMEOUT` 처리 |
| 6 | 결과 화면 | 화면 진입 | ⑤ `GET /result` | `type.headline`, `type.description`, `type.stylePoints`, `hairStyles[2]`, `fashionStyles[3]`, `disclaimer` 표시 | `ANALYSIS_INCOMPLETE` → `meta.nextStep` 화면으로 |
| 7 | [분석 상세보기] 팝업 | 버튼 탭 | ⑥ `GET /analysis-detail` | 얼굴형·체형 판정(`result`)과 설명(`description`)은 항상 표시. 확신도(`confidence`), 후보(`candidates`), 경계 여부(`borderline`), 안내(`warnings`), 측정값(`features`), Size Korea 비교(`body.compareToSizeKorea`)는 **값이 있을 때만** 표시 (`null`·`[]`이면 숨김) | — |
| 8 | 팝업 내 결과 수정 | 얼굴형/체형/모질 선택 후 [적용] | ⑦ `PATCH /profile` | `data.result`로 결과 화면 상태 교체, 팝업 정보 갱신이 필요하면 ⑥ 재호출 | `INVALID_REQUEST` → 선택 안내 |
| 9 | 챗봇 화면 (진입) | 결과 화면에서 [챗봇 이용하기] | ⑨ `GET /chat/messages` | `welcomeMessage`를 첫 말풍선으로 표시 → `messages[]` 순서대로 렌더링 → `suggestedPrompts` 칩 표시 → `remainingTurns` 표시(예: “남은 대화 19회”) | `ANALYSIS_INCOMPLETE`·`PROFILE_INPUT_REQUIRED` → `meta.nextStep` 화면으로 |
| 10 | 챗봇 화면 (대화) | 메시지 입력 후 [전송] 또는 추천 질문 칩 탭 | ⑩ `POST /chat/messages` | 사용자 말풍선 즉시 표시 + “입력 중…” 표시(응답 대기 중 전송·칩 비활성화) → `assistantMessage` 추가, `suggestedPrompts`·`remainingTurns` 교체 → `remainingTurns = 0`이면 입력창 비활성화 | `INVALID_REQUEST` → 입력 안내 / `CHAT_TURN_LIMIT_EXCEEDED` → 입력창·칩 비활성화 + `error.message` 표시 / `CHAT_SERVER_UNAVAILABLE`·`CHAT_TIMEOUT` → 사용자 말풍선에 실패 표시 + 입력 문장 유지 + [다시 보내기] (턴 차감 없음) |
| 11 | Moda 메인 화면 | 결과 화면에서 [Moda 시작하기] | 없음 (MVP) | 메인 화면 이동 (2차 기능 진입점) | — |
| 12 | 부스 초기화 | 메인/결과 화면의 숨김 버튼(예: 로고 3초 길게 누르기) | ⑧ `DELETE /sessions/{sessionId}` | 전역 상태(챗봇 대화 상태 포함)·로컬 사진 URI 삭제 → 시작 화면 | 실패해도 로컬 초기화 후 시작 화면 |
| 공통 | 모든 화면 | — | — | — | `SESSION_NOT_FOUND`·`SESSION_EXPIRED` → 로컬 초기화 후 시작 화면 |

### 6.1 권장 호출 순서

```text
[시작하기]      POST   /api/v1/sessions/guest                         → sessionId
[다음]          POST   /api/v1/sessions/{sessionId}/profile-input     → nextStep=FACE_ANALYSIS
[촬영]          POST   /api/v1/sessions/{sessionId}/analyze/face      → usable=false면 재촬영 반복 (usable=true일 때만 다음 화면)
[촬영/선택]     POST   /api/v1/sessions/{sessionId}/analyze/body      → usable=true일 때만 nextStep=RESULT_READY
(결과 진입)     GET    /api/v1/sessions/{sessionId}/result
[분석 상세보기] GET    /api/v1/sessions/{sessionId}/analysis-detail
[적용]          PATCH  /api/v1/sessions/{sessionId}/profile           → data.result로 화면 갱신
[챗봇 이용하기] GET    /api/v1/sessions/{sessionId}/chat/messages     → welcomeMessage + messages + suggestedPrompts
[전송/칩 탭]    POST   /api/v1/sessions/{sessionId}/chat/messages     → assistantMessage 추가 (최대 20턴)
[초기화]        DELETE /api/v1/sessions/{sessionId}                   → 챗봇 대화 내역도 함께 삭제
```

### 6.2 프론트엔드 구현 참고

- `sessionId`는 앱 전역 상태(예: Zustand/Context)에만 두고, 앱 재시작 시 새 세션으로 시작하는 것을 권장합니다(부스 체험 특성상 이어하기 불필요).
- 사진 업로드 예 (Expo, `fetch` 기준):
  ```ts
  const form = new FormData();
  form.append('image', { uri: photo.uri, name: 'face.jpg', type: 'image/jpeg' } as any);
  const res = await fetch(`${BASE_URL}/api/v1/sessions/${sessionId}/analyze/face`, {
    method: 'POST',
    body: form, // Content-Type 헤더를 직접 지정하지 않음(boundary 자동 설정)
  });
  const json: ApiResponse<FaceAnalysisData> = await res.json();
  ```
- 촬영 시 `quality: 0.7` 정도로 압축하면 10MB 제한과 업로드 시간을 모두 만족합니다.
- `score`는 화면에 표시하지 않습니다. 순위는 `rank`, 설명은 `reasons`만 사용합니다. (R-11)
- 챗봇: 대화 내역은 서버가 보관하므로 챗봇 화면을 나갔다 들어오면 ⑨를 다시 호출해 복원합니다. ⑩ 성공 응답의 `userMessage`로 낙관적(optimistic) 표시한 사용자 말풍선을 교체하고, 실패(`503`·`504`) 시에는 입력 문장을 유지해 재전송할 수 있게 합니다.

---

## 부록 A. 백엔드 ↔ AI 서버 내부 연동 스펙 (AI 팀: 김민건·최기완)

앱은 AI 서버를 직접 호출하지 않습니다. **백엔드(`ExternalAiAnalysisClient`)만** AI 서버를 호출하고, 결과를 3장 형식(camelCase + 한글 라벨)으로 변환해 앱에 전달합니다.

### A.1 엔드포인트

| Method | URL | Content-Type | 요청 필드 | 설명 |
|---|---|---|---|---|
| `GET` | `{AI_BASE_URL}/health` | — | — | 상태 확인. `{"status":"ok","version":"0.1.0"}` |
| `POST` | `{AI_BASE_URL}/v1/analyze/face` | `multipart/form-data` | `image` (file, 필수), `request_id` (string, 선택) | 얼굴형 분석 |
| `POST` | `{AI_BASE_URL}/v1/analyze/body` | `multipart/form-data` | `image` (file, 필수), `height_cm` (int, 필수), `request_id` (string, 선택) | 체형 분석 |

- 백엔드 설정 (`application.yml`): `moda.ai.mode: mock | external`, `moda.ai.base-url`, 연결 타임아웃 3초, 읽기 타임아웃 얼굴 15초 / 전신 20초.
- **사진이 판정 불가여도 HTTP 200**으로 `usable: false`를 반환합니다. 4xx/5xx는 서버 자체 오류에만 사용합니다.
- 백엔드는 이미지를 디스크에 쓰지 않고 메모리의 바이트 그대로 multipart로 전달합니다. AI 서버도 요청 이미지를 **디스크에 저장하지 않고 메모리에서 처리하는 방식을 우선**하며, 불가피하게 임시 파일을 만들면 처리 후 즉시 삭제합니다. (R-01)

### A.2 얼굴 분석 응답 (AI → 백엔드, snake_case)

> **v1.2 (AI 최신 코드 `v8`)**: 평균 대비 특징을 요약한 문장 `reason`(string | null, **선택**)이 추가되었습니다. 누락·`null`이어도 정상 처리합니다(A.4).

```json
{
  "usable": true,
  "face_shape": "heart",
  "reason": "평균 대비 이마 너비 비율이 높고 턱끝 너비 비율이 낮아, 이마에서 턱끝으로 갸름하게 모이는 하트형 특징이 보입니다.",
  "confidence": 0.64,
  "borderline": false,
  "candidates": [
    { "key": "heart", "prob": 0.64 },
    { "key": "diamond", "prob": 0.21 },
    { "key": "oval", "prob": 0.09 },
    { "key": "round", "prob": 0.03 },
    { "key": "long", "prob": 0.02 },
    { "key": "rectangle", "prob": 0.01 }
  ],
  "warnings": [],
  "features": {
    "length_ratio": 1.18,
    "forehead_ratio": 0.93,
    "jaw_ratio": 0.69,
    "chin_ratio": 0.62,
    "jaw_angle_deg": 141.5
  },
  "compare_to_size_korea": null,
  "quality": { "yaw_deg": 3.2, "pitch_deg": 5.1, "face_width_px": 388, "jaw_open": 0.04, "smile": 0.12 },
  "model_version": "face-rule-0.1"
}
```

### A.3 전신 분석 응답 (AI → 백엔드, snake_case)

**(1) 전체 필드 예시** (부가 정보를 모두 줄 수 있는 경우)

```json
{
  "usable": true,
  "body_type": "small_inv_triangle",
  "confidence": 0.68,
  "borderline": false,
  "candidates": [
    { "key": "small_inv_triangle", "prob": 0.68 },
    { "key": "standard", "prob": 0.22 },
    { "key": "rectangle", "prob": 0.07 },
    { "key": "large_rectangle", "prob": 0.03 }
  ],
  "warnings": [],
  "features": {
    "shoulder_dev": 0.040,
    "trunk_dev": -0.031,
    "shoulder_minus_trunk": 0.071
  },
  "compare_to_size_korea": {
    "reference": "SIZE_KOREA_20S_MALE",
    "height_cm": 174,
    "items": [
      { "key": "bideltoid_width", "user_mm": 491, "ref_mm": 470, "deviation": 0.040 },
      { "key": "waist_width", "user_mm": 261, "ref_mm": 271, "deviation": -0.041 },
      { "key": "hip_width", "user_mm": 325, "ref_mm": 330, "deviation": -0.020 },
      { "key": "shoulder_height", "user_mm": 1420, "ref_mm": 1399, "deviation": 0.010 },
      { "key": "crotch_height", "user_mm": 814, "ref_mm": 794, "deviation": 0.020 },
      { "key": "arm_length", "user_mm": 584, "ref_mm": 581, "deviation": 0.001 }
    ]
  },
  "quality": { "min_visibility": 0.83, "shoulder_tilt_deg": 2.1 },
  "model_version": "body-rule-0.1"
}
```

**(2) 최소 필드 예시** (유료 API가 체형 판정만 주는 경우 — 이것만으로도 정상 처리)

```json
{
  "usable": true,
  "body_type": "small_inv_triangle",
  "warnings": []
}
```

**(3) 분석 불가 예시**

```json
{
  "usable": false,
  "body_type": null,
  "warnings": ["BODY_LOW_VISIBILITY"]
}
```

### A.4 AI 응답 필드 규칙과 백엔드 변환

얼굴과 체형의 필수 범위가 다릅니다. 체형은 유료 API 도입 여부가 미확정이므로 **`usable`, `body_type`, `warnings`만 필수**입니다.

| AI 필드 (snake_case) | 타입 | 얼굴 | 체형 | 규칙 | 앱 응답 필드 (camelCase) |
|---|---|:-:|:-:|---|---|
| `usable` | bool | 필수 | 필수 | 판정 가능 여부 | `usable`, `retakeRequired` |
| `face_shape` / `body_type` | string \| null | 필수 | 필수 | `usable=true`면 **반드시 6종 / 4종 영문 Key 중 하나**, `usable=false`면 `null` 허용. 그 외 값이면 `502 AI_BAD_RESPONSE` | `faceShape` / `bodyType` (`CodeLabel`) |
| `warnings` | string[] | 필수 | 필수 | A.5의 코드 문자열 배열, 없으면 `[]` | `warnings` (`code`, `severity`, `message`) |
| `reason` | string \| null | **선택** (`v8`) | — | 평균 대비 특징 요약 문장(한국어 1~2문장). `usable=false`면 `null`. `style-rules.md` 7장 표현 가이드 준수 요청. 백엔드는 가공 없이 그대로 전달 | `FaceDetail.aiReason` (없으면 `null`) |
| `confidence` | float \| null | 필수 | **선택** | 1위 후보 확률 0~1 | `confidence` (없으면 `null`) |
| `borderline` | bool \| null | 필수 | **선택** | 얼굴: 1위 확률 < `FACE_MIN_CONFIDENCE`(0.60). 체형: AI 팀 기준 | `borderline` (없으면 `null`) |
| `candidates` | `[{key, prob}]` \| null | 필수 | **선택** | 확률 합 ≈ 1.0, 내림차순 | `candidates` (`key`, `label`, `probability`, 없으면 `[]`) |
| `features` | object \| null | 필수 | **선택** | 숫자 값만. 키 추가는 자유(백엔드에 라벨 없으면 키 그대로 표시) | `features` (배열로 변환, 없으면 `[]`) |
| `compare_to_size_korea` | object \| null | `null` | **선택** | `items[].key`는 `config.py` `SIZE_KOREA_REF_MM` 키 사용(`height` 제외) | `compareToSizeKorea` (없으면 `null`) |
| `quality` | object | 선택 | 선택 | 촬영 품질 원시값 (백엔드 로그용, 앱 미노출) | — |
| `model_version` | string | 선택 | 선택 | 로그용 | — |

체형 선택 필드 처리 규칙 (백엔드)

1. 선택 필드가 **누락되었거나 `null`이면 `502`를 내지 않고**, 앱 응답에서 `null`(단일 값) 또는 `[]`(배열)로 내려줍니다.
2. 선택 필드의 **형식이 잘못된 경우**(예: `candidates`에 정의되지 않은 Key, `prob`가 숫자가 아님, `compare_to_size_korea.items` 누락)에도 `502`를 내지 않고 해당 필드만 `null`/`[]`로 처리한 뒤 서버 로그에 경고를 남깁니다.
3. 체형의 `502 AI_BAD_RESPONSE`는 **필수 3필드**(`usable`, `body_type`, `warnings`)가 누락·형식 오류이거나, `usable=true`인데 `body_type`이 4종 Key가 아닐 때만 발생합니다.
4. 얼굴 응답은 기존대로 위 표의 ‘필수’ 필드가 모두 있어야 합니다(MediaPipe 자체 분석).
5. 얼굴 `reason`(`v8`)은 **선택 필드**입니다. 누락·`null`·빈 문자열·문자열이 아닌 값이면 `502`를 내지 않고 `aiReason = null`로 처리합니다(형식 오류 시 서버 로그에 경고). `v8` 이전 AI 서버와도 그대로 호환됩니다.

**features 라벨 (백엔드 `analysis-labels.json`)**

| 키 | 라벨 | unit | description | 비고 |
|---|---|---|---|---|
| `length_ratio` | 얼굴 길이 비율 | `ratio` | 얼굴 길이 ÷ 광대 폭 | |
| `forehead_ratio` | 이마 너비 비율 | `ratio` | 이마 폭 ÷ 광대 폭 | |
| `jaw_ratio` | 턱 너비 비율 | `ratio` | 턱 폭 ÷ 광대 폭 | |
| `chin_ratio` | 턱끝 너비 비율 | `ratio` | 턱끝 폭 ÷ 광대 폭 | |
| `jaw_angle_deg` | 턱 각도 | `deg` | 턱 윤곽선이 이루는 각도 | |
| `shoulder_dev` | 어깨 편차 | `percent` | 키 대비 어깨 너비의 평균 대비 차이 | AI는 비율(0.040) → 백엔드가 ×100 |
| `trunk_dev` | 몸통 편차 | `percent` | 키 대비 허리·엉덩이 너비의 평균 대비 차이 | 동일 |
| `shoulder_minus_trunk` | 어깨-몸통 편차 차이 | `percent` | 어깨 편차 − 몸통 편차 | 동일 |

> 체형 features 키 3종은 `config.py`의 `BODY_RULES` 계산을 기준으로 한 **제안 키**입니다. AI 팀에서 이름을 바꾸면 이 표만 함께 수정하면 됩니다.

**Size Korea 항목 라벨**

| 키 (`SIZE_KOREA_REF_MM`) | Size Korea 항목 | 라벨 |
|---|---|---|
| `bideltoid_width` | (318) 위팔사이너비 | 어깨 너비 |
| `waist_width` | (118) 허리너비 | 허리 너비 |
| `hip_width` | (120) 엉덩이너비 | 엉덩이 너비 |
| `shoulder_height` | (105) 어깨높이 | 어깨 높이 |
| `crotch_height` | (110) 샅높이 | 다리 길이(샅높이) |
| `arm_length` | (236) 팔길이 | 팔 길이 |

### A.5 경고 코드표 (AI가 `warnings`에 넣는 코드)

백엔드가 코드별 `severity`와 `message`를 붙여 앱에 전달합니다. AI는 **코드 문자열만** 보내면 됩니다.

| 코드 | 대상 | severity | 발생 기준 (`config.py`) | 앱 표시 message |
|---|---|---|---|---|
| `FACE_NOT_DETECTED` | 얼굴 | `RETAKE` | 얼굴 미검출 | 얼굴이 인식되지 않았어요. 화면 가이드 안에 얼굴을 맞춰 주세요. |
| `MULTIPLE_FACES` | 얼굴 | `RETAKE` | 2명 이상 검출 | 한 명만 화면에 나오도록 촬영해 주세요. |
| `FACE_YAW_EXCEEDED` | 얼굴 | `RETAKE` | 좌우 회전 > `FACE_MAX_YAW_DEG`(10°) | 고개를 좌우로 돌리지 말고 정면을 바라봐 주세요. |
| `FACE_PITCH_EXCEEDED` | 얼굴 | `RETAKE` | 상하 각도 > `FACE_MAX_PITCH_DEG`(15°) | 고개를 숙이거나 들지 말고 정면을 바라봐 주세요. |
| `FACE_TOO_SMALL` | 얼굴 | `RETAKE` | 얼굴 폭 < `FACE_MIN_WIDTH_PX`(200px) | 카메라에 조금 더 가까이 다가와 주세요. |
| `MOUTH_OPEN` | 얼굴 | `RETAKE` | 입 벌림 > `FACE_MAX_JAW_OPEN`(0.15) | 입을 다문 자연스러운 표정으로 촬영해 주세요. |
| `SMILING` | 얼굴 | `RETAKE` | 웃음 > `FACE_MAX_SMILE`(0.35) | 무표정에 가까운 편안한 표정으로 촬영해 주세요. |
| `FACE_BORDERLINE` | 얼굴 | `INFO` | `borderline=true` (백엔드가 자동 추가 가능) | 여러 얼굴형의 특징이 함께 보여요. [분석 상세보기]에서 결과를 확인하고 수정할 수 있어요. |
| `BODY_NOT_DETECTED` | 전신 | `RETAKE` | 사람 미검출 | 전신이 인식되지 않았어요. 머리부터 발끝까지 화면에 들어오게 촬영해 주세요. |
| `MULTIPLE_PEOPLE` | 전신 | `RETAKE` | 2명 이상 검출 | 한 명만 나오도록 촬영해 주세요. |
| `BODY_LOW_VISIBILITY` | 전신 | `RETAKE` | 어깨·골반·무릎·발목 신뢰도 < `BODY_MIN_VISIBILITY`(0.6) | 어깨·골반·무릎·발목이 잘 보이도록 촬영해 주세요. |
| `SHOULDER_TILT` | 전신 | `RETAKE` | 어깨 기울기 > `BODY_MAX_SHOULDER_TILT_DEG`(6°) | 어깨가 수평이 되도록 똑바로 서 주세요. |
| `BODY_BORDERLINE` | 전신 | `INFO` | `borderline=true` (백엔드가 자동 추가 가능) | 두 체형의 경계에 가까워요. [분석 상세보기]에서 확인하고 수정할 수 있어요. |
| `ANALYSIS_UNUSABLE` | 공통 | `RETAKE` | `usable=false`인데 `RETAKE` 코드가 하나도 없을 때 **백엔드가 자동 추가** | 사진을 분석할 수 없어요. 가이드에 맞춰 다시 촬영해 주세요. |
| (정의되지 않은 코드) | 공통 | `INFO` | — | 촬영 상태를 한 번 더 확인해 주세요. |

- `usable=false`이면 `RETAKE` 코드를 1개 이상 넣는 것을 권장합니다. 유료 API처럼 사유 코드를 줄 수 없으면 `[]`로 보내도 되며, 백엔드가 `ANALYSIS_UNUSABLE`을 추가합니다.
- `usable=true`인데 `RETAKE` 코드가 섞여 있으면 백엔드는 해당 경고를 `INFO`로 낮춰 전달합니다(분석은 성공 처리).
- `FACE_BORDERLINE`/`BODY_BORDERLINE`은 `borderline=true`일 때만 추가합니다. 체형 `borderline`이 `null`이면 추가하지 않습니다.

### A.6 백엔드의 AI 오류 처리

| AI 서버 상황 | 백엔드 → 앱 |
|---|---|
| 연결 실패 / `5xx` | `503 AI_SERVER_UNAVAILABLE` |
| 읽기 타임아웃 | `504 AI_TIMEOUT` |
| JSON 파싱 실패, 필수 필드 누락·형식 오류(A.4 기준: 얼굴은 표의 필수 전체, 체형은 `usable`·`body_type`·`warnings`), `usable=true`인데 정의되지 않은 얼굴형·체형 Key | `502 AI_BAD_RESPONSE` |
| 체형 선택 필드 누락·`null`·형식 오류 | 에러 없음, 해당 필드만 `null`/`[]` (A.4 처리 규칙) |
| 얼굴 `reason` 누락·`null`·형식 오류 (`v8`) | 에러 없음, `aiReason = null` (A.4 규칙 5) |
| `200` + `usable=false` | `200` + `usable=false` (에러 아님) |

---

## 부록 B. TypeScript 타입 정의 (Expo 프로젝트에 복사해 사용)

```ts
// src/api/types.ts — docs/api-spec.md v1.2 기준

export type FaceShapeKey = 'diamond' | 'heart' | 'oval' | 'rectangle' | 'round' | 'long';
export type BodyTypeKey = 'standard' | 'small_inv_triangle' | 'large_rectangle' | 'rectangle';
export type HairThickness = 'THIN' | 'MEDIUM' | 'THICK';
export type HairCurl = 'STRAIGHT' | 'SEMI_CURLY' | 'CURLY';
export type HairDensity = 'LOW' | 'MEDIUM' | 'HIGH';
export type HairStyleCode = 'UP_STYLE' | 'PARTED_STYLE' | 'DANDY_STYLE' | 'CROP_STYLE' | 'SHADOW_STYLE';
export type FashionStyleCode =
  | 'CASUAL' | 'DANDY' | 'MINIMAL' | 'CITY_BOY' | 'AMEKAJI'
  | 'STREET' | 'SPORTY' | 'GORPCORE' | 'VINTAGE';
export type NextStep = 'PROFILE_INPUT' | 'FACE_ANALYSIS' | 'BODY_ANALYSIS' | 'RESULT_READY';
export type AnalysisSource = 'AI' | 'MANUAL';
export type AnalysisStatus = 'ANALYZED' | 'CORRECTED' | 'MANUAL' | 'NOT_ANALYZED';
export type WarningSeverity = 'RETAKE' | 'INFO';
export type SizeKoreaDirection = 'ABOVE' | 'BELOW' | 'SIMILAR';
export type ChatRole = 'USER' | 'ASSISTANT';

export type ErrorCode =
  | 'INVALID_REQUEST' | 'PHOTO_CONSENT_REQUIRED' | 'IMAGE_REQUIRED'
  | 'SESSION_NOT_FOUND' | 'PROFILE_INPUT_REQUIRED' | 'ANALYSIS_INCOMPLETE'
  | 'ANALYSIS_IN_PROGRESS' | 'SESSION_EXPIRED' | 'IMAGE_TOO_LARGE'
  | 'UNSUPPORTED_IMAGE_TYPE' | 'INTERNAL_ERROR' | 'AI_BAD_RESPONSE'
  | 'AI_SERVER_UNAVAILABLE' | 'AI_TIMEOUT'
  | 'CHAT_TURN_LIMIT_EXCEEDED' | 'CHAT_SERVER_UNAVAILABLE' | 'CHAT_TIMEOUT'; // v1.2 챗봇

export interface CodeLabel<K extends string = string> { key: K; label: string; }

export interface Progress { profileInput: boolean; faceAnalysis: boolean; bodyAnalysis: boolean; }

export interface Warning { code: string; severity: WarningSeverity; message: string; }

export interface FieldError { field: string; reason: string; rejectedValue: unknown | null; }

export interface ApiError {
  status: number;
  code: ErrorCode;
  message: string;
  details: FieldError[];
  meta: Record<string, unknown> | null;
  path: string;
  timestamp: string;
}

export type ApiResponse<T> = { success: true; data: T } | { success: false; error: ApiError };

// ① POST /sessions/guest
export interface GuestSessionRequest { photoConsent: true; deviceLabel?: string | null; }
export interface GuestSessionData {
  sessionId: string;
  createdAt: string;
  expiresAt: string;
  ttlMinutes: number;
  progress: Progress;
  nextStep: NextStep;
}

// ② POST /profile-input
export interface ProfileInput {
  age: number;
  heightCm: number;
  weightKg: number | null;
  hairThickness: HairThickness;
  hairCurl: HairCurl;
  hairDensity: HairDensity;
}
export type ProfileInputRequest = Omit<ProfileInput, 'weightKg'> & { weightKg?: number | null };
export interface ProfileInputData {
  sessionId: string;
  profileInput: ProfileInput;
  bodyAnalysisReset: boolean;
  progress: Progress;
  nextStep: NextStep;
  expiresAt: string;
}

// ③ POST /analyze/face
export interface FaceAnalysisData {
  sessionId: string;
  analysisType: 'FACE';
  usable: boolean;
  retakeRequired: boolean;
  faceShape: CodeLabel<FaceShapeKey> | null;
  confidence: number | null;
  borderline: boolean | null;
  warnings: Warning[];
  progress: Progress;
  nextStep: NextStep;
  analyzedAt: string;
  expiresAt: string;
}

// ④ POST /analyze/body
export interface BodyAnalysisData {
  sessionId: string;
  analysisType: 'BODY';
  usable: boolean;
  retakeRequired: boolean;
  bodyType: CodeLabel<BodyTypeKey> | null;
  confidence: number | null; // usable=true여도 null 가능 (체형 유료 API)
  borderline: boolean | null; // 동일
  heightCmUsed: number;
  warnings: Warning[];
  progress: Progress;
  nextStep: NextStep;
  analyzedAt: string;
  expiresAt: string;
}

// ⑤ GET /result
export interface Adjustment {
  code: string;
  factor: 'hairThickness' | 'hairCurl' | 'hairDensity' | 'bmi' | 'height' | 'age';
  value: string;
  delta: number;
}
export interface ScoreBreakdown { base: number; adjustments: Adjustment[]; }

export interface HairRecommendation {
  rank: number;
  code: HairStyleCode;
  name: string;
  description: string;
  imageKey: string;
  score: number;
  reasons: string[];
  reasonCodes: string[];
  scoreBreakdown: ScoreBreakdown;
}
export interface FashionRecommendation extends Omit<HairRecommendation, 'code'> {
  code: FashionStyleCode;
  itemKeywords: string[];
}

export interface TypeResult {
  typeCode: string;
  typeName: string;
  nameConfirmed: boolean;
  headline: string;
  description: string;
  stylePoints: string[];
}
export interface ResultBasis {
  faceShape: CodeLabel<FaceShapeKey>;
  bodyType: CodeLabel<BodyTypeKey>;
  faceSource: AnalysisSource;
  bodySource: AnalysisSource;
}
export interface ResultData {
  sessionId: string;
  ruleVersion: string;
  generatedAt: string;
  type: TypeResult;
  basis: ResultBasis;
  hairStyles: HairRecommendation[];
  fashionStyles: FashionRecommendation[];
  disclaimer: string;
}

// ⑥ GET /analysis-detail
export interface Candidate { key: string; label: string; probability: number; }
export interface Feature {
  key: string;
  label: string;
  value: number;
  unit: 'ratio' | 'deg' | 'percent';
  description: string | null;
}
export interface SizeKoreaItem {
  key: string;
  label: string;
  userMm: number;
  refMm: number;
  deviation: number;
  deviationPercent: number;
  direction: SizeKoreaDirection;
}
export interface SizeKoreaCompare {
  reference: string;
  referenceLabel: string;
  heightCm: number;
  items: SizeKoreaItem[];
  summary: { shoulderDeviation: number; trunkDeviation: number } | null;
}
export interface FaceDetail {
  status: AnalysisStatus;
  source: AnalysisSource | null;
  result: CodeLabel<FaceShapeKey> | null;
  aiResult: CodeLabel<FaceShapeKey> | null;
  description: string | null;
  aiReason: string | null; // v1.2 (AI v8 `reason`), 얼굴 전용. AI 미제공 시 null
  confidence: number | null;
  borderline: boolean | null;
  candidates: Candidate[];
  warnings: Warning[];
  features: Feature[];
  analyzedAt: string | null;
}
// 체형은 status가 ANALYZED여도 confidence/borderline=null, candidates/features=[], compareToSizeKorea=null 가능 → 값이 있을 때만 렌더링
export interface BodyDetail extends Omit<FaceDetail, 'result' | 'aiResult' | 'aiReason'> {
  result: CodeLabel<BodyTypeKey> | null;
  aiResult: CodeLabel<BodyTypeKey> | null;
  heightCmUsed: number | null;
  compareToSizeKorea: SizeKoreaCompare | null;
}
export interface AnalysisDetailData {
  sessionId: string;
  face: FaceDetail;
  body: BodyDetail;
  profileInput: ProfileInput | null;
  editableOptions: {
    faceShapes: CodeLabel<FaceShapeKey>[];
    bodyTypes: CodeLabel<BodyTypeKey>[];
  };
}

// ⑦ PATCH /profile
export interface ProfilePatchRequest {
  faceShape?: FaceShapeKey | null;
  bodyType?: BodyTypeKey | null;
  hairThickness?: HairThickness | null;
  hairCurl?: HairCurl | null;
  hairDensity?: HairDensity | null;
}
export interface ProfilePatchData {
  sessionId: string;
  changedFields: string[];
  progress: Progress;
  nextStep: NextStep;
  expiresAt: string;
  result: ResultData | null;
}

// ⑧ DELETE /sessions/{sessionId}
export interface SessionDeleteData {
  sessionId: string;
  deleted: true;
  alreadyDeleted: boolean;
  deletedPhotoCount: number;
  deletedAt: string;
}

// ⑨·⑩ 챗봇 공통
export interface ChatMessage {
  messageId: string;
  role: ChatRole;
  content: string;
  createdAt: string;
}

// ⑨ GET /chat/messages
export interface ChatHistoryData {
  sessionId: string;
  welcomeMessage: string;     // 저장·턴 차감 없음, 항상 첫 말풍선으로 표시
  messages: ChatMessage[];    // createdAt 오름차순, 없으면 []
  suggestedPrompts: string[]; // 3~4개, remainingTurns=0이면 []
  remainingTurns: number;     // 0 ~ 20
  maxTurns: number;           // 20
  expiresAt: string;
}

// ⑩ POST /chat/messages
export interface ChatSendRequest { message: string; } // trim 후 1~500자, 공백만 불가
export interface ChatSendData {
  sessionId: string;
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  suggestedPrompts: string[];
  remainingTurns: number;
  maxTurns: number;
  expiresAt: string;
}
```

---

## 부록 C. curl 테스트 예시 (Swagger 대신 터미널에서 확인)

```bash
BASE=http://localhost:8080/api/v1

# ① 세션 생성
curl -s -X POST $BASE/sessions/guest \
  -H "Content-Type: application/json" \
  -d '{"photoConsent":true,"deviceLabel":"booth-01"}'
# → data.sessionId 값을 SID에 저장
SID=3f6c1e2a-8b4d-4c1f-9a7e-2d5b6c8e9f01

# ② 기본 정보
curl -s -X POST $BASE/sessions/$SID/profile-input \
  -H "Content-Type: application/json" \
  -d '{"age":21,"heightCm":174,"weightKg":63,"hairThickness":"THIN","hairCurl":"SEMI_CURLY","hairDensity":"MEDIUM"}'

# ③ 얼굴 분석 (Mock 모드: 하트형 지정)
curl -s -X POST $BASE/sessions/$SID/analyze/face \
  -H "X-Mock-Face-Shape: heart" \
  -F "image=@./face.jpg;type=image/jpeg"

# ④ 전신 분석 (Mock 모드: 작은 역삼각형 지정)
curl -s -X POST $BASE/sessions/$SID/analyze/body \
  -H "X-Mock-Body-Type: small_inv_triangle" \
  -F "image=@./body.jpg;type=image/jpeg"

# ⑤ 결과
curl -s $BASE/sessions/$SID/result

# ⑥ 상세
curl -s $BASE/sessions/$SID/analysis-detail

# ⑦ 수동 보정
curl -s -X PATCH $BASE/sessions/$SID/profile \
  -H "Content-Type: application/json" \
  -d '{"faceShape":"diamond"}'

# ⑨ 챗봇 진입 (대화 내역 + 첫 인사 + 추천 질문)
curl -s $BASE/sessions/$SID/chat/messages

# ⑩ 챗봇 메시지 전송 (moda.chat.mode=mock: 지연 2초 지정)
curl -s -X POST $BASE/sessions/$SID/chat/messages \
  -H "Content-Type: application/json" \
  -H "X-Mock-Delay-Ms: 2000" \
  -d '{"message":"가르마 스타일은 어떻게 연출하면 좋을까요?"}'

# ⑧ 초기화 (챗봇 대화 내역도 함께 삭제)
curl -s -X DELETE $BASE/sessions/$SID
```
