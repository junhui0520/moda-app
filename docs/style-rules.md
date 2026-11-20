# Moda 추천 규칙 및 유형 구조 정의서 (`docs/style-rules.md`)

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.1-draft (`ruleVersion: "v1.1-draft"`) |
| 작성 | 백엔드 박준희 |
| 대상 | 프론트엔드 이상현(Expo), AI 김민건·최기완, 팀 전체 |
| 관련 문서 | `docs/api-spec.md`, `docs/recommendation-dataset.csv`, 기능정의서(F-03, F-04, F-07), AI `config(확정아님테스트용).py` |
| 적용 범위 | 대회 MVP (2026-11-29 접수 / 12-03 심사) |

> 이 문서는 백엔드 추천 엔진의 **단일 기준(Single Source of Truth)**입니다. 점수표·문구를 바꾸면 반드시 이 문서와 `ruleVersion`을 함께 올리고 팀 채널에 공유합니다.
> 프론트엔드는 이 문서의 한글 명칭·유형명을 **앱 코드에 하드코딩하지 않습니다.** 화면에 표시하는 텍스트는 모두 API 응답(`label`, `name`, `typeName`, `headline`, `reasons` 등)을 그대로 사용합니다. (R-07)

---

## 0. 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| v1.0-draft | 2026-11-19 | 최초 작성. 헤어·패션 점수표, 보정 규칙, `type_code` 12종 구조, 시드 파일 분리 방식, 네이밍 컨셉 초안 |
| v1.1-draft | 2026-11-20 | 팀 확정 반영: 맞춤 유형 **24종(체형 4 × 얼굴형 6)**으로 개편, 얼굴형 묶음(그룹) 개념 전면 삭제, 분석 불가 사진은 재촬영만 허용(“직접 선택하기” 자동 노출 규칙 삭제), 체형 AI 응답 선택 필드 완화 반영, `nameConfirmed` 필드명 통일, Q-04 오타 수정. 헤어·패션 점수표와 문구는 변경 없음 |

---

## 1. 공통 코드 사전

### 1.1 얼굴형 (6종) — `faceShape`

AI `config.py`의 `FACE_LABELS` 키를 그대로 사용합니다. **소문자 영문 Key**입니다.

| Key | 공식 한글명 | `faceIndex` (유형 산출용) |
|---|---|:-:|
| `diamond` | 다이아몬드형 | 0 |
| `heart` | 하트형 | 1 |
| `oval` | 계란형 | 2 |
| `rectangle` | 직사각형 | 3 |
| `round` | 둥근형 | 4 |
| `long` | 긴형 | 5 |

### 1.2 맞춤 유형 산출 인덱스

맞춤 유형은 얼굴형 6종과 체형 4종을 **묶지 않고 그대로 조합**한 24종입니다(5장). 얼굴형을 그룹으로 묶는 중간 개념은 사용하지 않습니다.

```text
typeCode = "TYPE-" + 2자리 0채움(bodyIndex × 6 + faceIndex + 1)     # TYPE-01 ~ TYPE-24
faceIndex : 1.1 표의 순서 (diamond=0 … long=5)
bodyIndex : 1.3 표의 순서 (standard=0 … rectangle=3)
```

### 1.3 체형 (4종) — `bodyType`

**소문자 영문 Key**입니다. 백엔드는 AI 응답의 **Key만** 사용하고, 화면 표시명은 아래 **공식 한글명**으로 일괄 매핑합니다.

| Key | 공식 한글명 (화면 표시) | `bodyIndex` (유형 산출용) | AI 코드 내부 한글명 (`BODY_LABELS`, 표시 금지) | 판정 기준 요약 (AI `BODY_RULES`, 임시) |
|---|---|:-:|---|---|
| `standard` | 표준유형 | 0 | 표준체형 | 어깨·몸통 편차가 모두 ±5% 이내 |
| `small_inv_triangle` | 작은 역삼각형 | 1 | 작은 역삼각체형 | 몸통 편차 ≤ 0% 이고 (어깨 편차 − 몸통 편차) ≥ +6% |
| `large_rectangle` | 큰 사각 체형 | 2 | 큰 사각체형 | 몸통 굵기 편차 ≥ +8% |
| `rectangle` | 사각 체형 | 3 | 사각체형 | 위 3종에 해당하지 않음 (몸통 굵기가 고르고 어깨 보통) |

> 편차 = (내 값 ÷ 키) ÷ (Size Korea 20대 남성 평균 값 ÷ 평균 키) − 1. 판정 임계값은 AI 팀의 calibrate 결과로 바뀔 수 있으며, **Key 4종은 고정**입니다.
> 체형 분석은 유료 API 도입 여부가 미확정이므로, 판정 방식이 바뀌어도 백엔드는 **AI가 반환한 Key 4종 중 하나**만 받으면 동작합니다. 확신도·후보·측정값 등 부가 정보는 선택 항목입니다(`api-spec.md` 부록 A.4).

### 1.4 모질 입력 Enum (3항목)

| 필드 | 값 | 화면 표시 예시 |
|---|---|---|
| `hairThickness` (굵기) | `THIN` | 가는 편 |
| | `MEDIUM` | 보통 |
| | `THICK` | 굵은 편 |
| `hairCurl` (곱슬 정도) | `STRAIGHT` | 직모 |
| | `SEMI_CURLY` | 반곱슬 |
| | `CURLY` | 곱슬 |
| `hairDensity` (숱) | `LOW` | 적은 편 |
| | `MEDIUM` | 보통 |
| | `HIGH` | 많은 편 |

> 입력 화면의 선택지 라벨(“가는 편” 등)은 프론트엔드 UI 문구이므로 자유롭게 다듬어도 됩니다. **서버로 보내는 값은 반드시 위 Enum 문자열**입니다.

### 1.5 헤어스타일 (5종) — `hairStyle.code`

| code | 공식 명칭 (`name`) | 정의 (`description`) | `imageKey` |
|---|---|---|---|
| `UP_STYLE` | 깐머리 스타일 | 앞머리를 위나 뒤로 넘겨 이마를 드러내는 스타일 (포마드·리젠트·애즈 업 등) | `hair_up_style` |
| `PARTED_STYLE` | 가르마 스타일 | 2:8~5:5 가르마로 이마를 일부 드러내고 옆 볼륨을 살린 스타일 (가르마펌 등) | `hair_parted_style` |
| `DANDY_STYLE` | 댄디 스타일 | 앞머리를 자연스럽게 내려 이마를 덮는 단정한 스타일 (댄디컷 등) | `hair_dandy_style` |
| `CROP_STYLE` | 크롭 스타일 | 옆·뒷머리를 짧게 정리하고 짧은 앞머리에 질감을 준 스타일 (크롭컷·페이드 등) | `hair_crop_style` |
| `SHADOW_STYLE` | 쉐도우 스타일 | 앞머리를 내리고 C컬·S컬 웨이브로 볼륨을 준 스타일 (쉐도우펌 등) | `hair_shadow_style` |

### 1.6 패션스타일 (9종) — `fashionStyle.code`

| code | 공식 명칭 (`name`) | 정의 (`description`) | 아이템 키워드 (`itemKeywords`, 순서 고정) | `imageKey` |
|---|---|---|---|---|
| `CASUAL` | 캐주얼 | 셔츠·맨투맨·데님 등 기본 아이템 중심의 편안한 스타일 | 옥스퍼드 셔츠, 맨투맨, 치노 팬츠, 데님 팬츠, 스니커즈 | `fashion_casual` |
| `DANDY` | 댄디 | 블레이저·셔츠·슬랙스 중심의 단정한 스타일 | 블레이저, 셔츠, 니트 폴로, 슬랙스, 로퍼 | `fashion_dandy` |
| `MINIMAL` | 미니멀 | 장식을 덜어낸 모노톤·뉴트럴 컬러 중심의 스타일 | 무지 니트, 모노톤 티셔츠, 와이드 슬랙스, 싱글 코트, 더비 슈즈 | `fashion_minimal` |
| `CITY_BOY` | 시티보이 | 여유 있는 오버핏과 레이어드 중심의 도시적인 스타일 | 오버핏 셔츠, 와이드 치노 팬츠, 볼캡, 토트백, 레트로 러닝화 | `fashion_city_boy` |
| `AMEKAJI` | 아메카지 | 워크웨어·데님·플란넬 등 미국 캐주얼을 재해석한 스타일 | 워크 재킷, 플란넬 셔츠, 셀비지 데님, 니트 베스트, 워커 부츠 | `fashion_amekaji` |
| `STREET` | 스트릿 | 박시한 실루엣과 그래픽 포인트 중심의 스타일 | 오버핏 후디, 그래픽 티셔츠, 카고 팬츠, 하이탑 스니커즈, 비니 | `fashion_street` |
| `SPORTY` | 스포티 | 트랙·기능성 소재 중심의 활동적인 스타일 | 트랙 재킷, 기능성 티셔츠, 조거 팬츠, 러닝화, 볼캡 | `fashion_sporty` |
| `GORPCORE` | 고프코어 | 아웃도어 기능성 아이템을 일상복으로 입는 스타일 | 셸 재킷, 플리스, 나일론 카고 팬츠, 트레일 러닝화, 크로스백 | `fashion_gorpcore` |
| `VINTAGE` | 빈티지 | 질감 있는 소재와 레트로 컬러 중심의 스타일 | 코듀로이 재킷, 워싱 데님, 레트로 스웨트셔츠, 체크 셔츠, 캔버스 스니커즈 | `fashion_vintage` |

> `imageKey`는 프론트엔드 로컬 에셋 파일명 규칙입니다. 예: `assets/styles/hair_parted_style.png`. 서버는 이미지를 내려주지 않습니다. (R-13: 이미지 출처·라이선스는 팀이 확보)

### 1.7 파생 구간 (백엔드 내부 계산, API로 노출하지 않음)

| 구간 | 계산 | 값 |
|---|---|---|
| BMI 구간 `bmiBand` | `weightKg ÷ (heightCm/100)²`, 소수 첫째 자리 반올림 | `BMI_LOW` (< 18.5) / `BMI_MID` (18.5 ~ 22.9) / `BMI_HIGH` (23.0 ~ 24.9) / `BMI_VERY_HIGH` (≥ 25.0) / `weightKg = null`이면 구간 없음(보정 생략) |
| 키 구간 `heightBand` | `heightCm` | `HEIGHT_SHORT` (< 170) / `HEIGHT_MID` (170 ~ 181) / `HEIGHT_TALL` (≥ 182) |
| 나이 구간 `ageBand` | `age` | `AGE_U22` (≤ 22) / `AGE_23_29` (23 ~ 29) / `AGE_30S` (30 ~ 39) / `AGE_40P` (≥ 40) |

> BMI 수치와 구간명은 **어떤 응답에도 포함하지 않고**, 화면에도 노출하지 않습니다. (R-11 외모 평가 배제)

---

## 2. 추천 엔진 공통 원칙

1. **결정적(Deterministic) 규칙 엔진**: 같은 입력이면 항상 같은 결과. 랜덤 요소 없음.
2. **점수 = 기본 점수(1차 기준) + 보정 점수(보조 기준)의 합** — 정수 연산.
   - 헤어: 1차 기준 = 얼굴형, 보정 = 모질 3항목
   - 패션: 1차 기준 = 체형, 보정 = BMI 구간·키 구간·나이 구간
3. **정렬 규칙** (상위 N개 선택: 헤어 2개, 패션 3개)
   1. 최종 점수 내림차순
   2. 동점이면 **기본 점수** 내림차순 (1차 기준을 우선)
   3. 그래도 동점이면 **코드 선언 순서** (1.5 / 1.6 표의 위→아래 순서, 즉 Java enum ordinal 오름차순)
4. **추천 근거(`reasons`) 생성 규칙** — 항목당 최대 2문장
   1. 1번째 문장: 기본 점수 근거 — “얼굴형×헤어” 또는 “체형×패션” 전용 문구(3.3 / 4.3). 전용 문구가 없는 조합은 스타일별 일반 문구 사용.
   2. 2번째 문장: 그 항목에 적용된 **양수(+) 보정** 중 delta가 가장 큰 것의 문구. delta 동점이면 헤어는 `굵기 → 곱슬 → 숱`, 패션은 `BMI → 키 → 나이` 순으로 1개만 선택. 양수 보정이 없으면 1문장만.
   3. 음수(−) 보정은 문구로 노출하지 않습니다. (`scoreBreakdown.adjustments`에는 기록)
5. `score`는 순위 산정용 내부 점수입니다. **프론트엔드는 점수를 %·별점 등으로 표시하지 않습니다.** 화면에는 `rank`와 `reasons`만 사용합니다.
6. 모든 문구는 7장 표현 가이드(R-11)를 따릅니다.

### 2.1 계산 의사코드

```text
function recommendHair(faceShape, hairThickness, hairCurl, hairDensity):
    for style in [UP_STYLE, PARTED_STYLE, DANDY_STYLE, CROP_STYLE, SHADOW_STYLE]:
        base  = HAIR_BASE[faceShape][style]                      # 3.1
        adjs  = [HAIR_ADJ[thickness=hairThickness][style],       # 3.2
                 HAIR_ADJ[curl=hairCurl][style],
                 HAIR_ADJ[density=hairDensity][style]]           # delta != 0 인 것만 기록
        score = base + sum(adjs.delta)
    sort by (score DESC, base DESC, ordinal ASC)
    take top 2 → rank 1, 2
    reasons = [baseReason(faceShape, style), bestPositiveAdj(adjs)?.reason]

function recommendFashion(bodyType, age, heightCm, weightKg?):
    bands = [bmiBand(heightCm, weightKg) if weightKg != null, heightBand(heightCm), ageBand(age)]
    for style in [CASUAL, DANDY, MINIMAL, CITY_BOY, AMEKAJI, STREET, SPORTY, GORPCORE, VINTAGE]:
        base  = FASHION_BASE[bodyType][style]                    # 4.1
        adjs  = [FASHION_ADJ[band][style] for band in bands]     # 4.2, delta != 0 인 것만 기록
        score = base + sum(adjs.delta)
    sort by (score DESC, base DESC, ordinal ASC)
    take top 3 → rank 1, 2, 3
    reasons = [baseReason(bodyType, style), bestPositiveAdj(adjs)?.reason]
```

---

## 3. 헤어스타일 추천 규칙

### 3.1 기본 점수표 (1차 기준: 얼굴형) — `HAIR_BASE` (0~5점)

| faceShape \ hair | `UP_STYLE` 깐머리 스타일 | `PARTED_STYLE` 가르마 스타일 | `DANDY_STYLE` 댄디 스타일 | `CROP_STYLE` 크롭 스타일 | `SHADOW_STYLE` 쉐도우 스타일 |
|---|:-:|:-:|:-:|:-:|:-:|
| `diamond` 다이아몬드형 | 3 | **5** | 4 | 2 | 4 |
| `heart` 하트형 | 2 | **5** | 4 | 3 | 4 |
| `oval` 계란형 | **5** | **5** | 4 | 4 | 4 |
| `rectangle` 직사각형 | 2 | 4 | 4 | 3 | **5** |
| `round` 둥근형 | **5** | 4 | 2 | 4 | 2 |
| `long` 긴형 | 1 | 4 | **5** | 3 | **5** |

설계 근거 요약

| 얼굴형 | 방향 |
|---|---|
| 다이아몬드형 | 관자놀이·이마 쪽에 볼륨을 주는 가르마/쉐도우, 이마를 덮는 댄디가 유리. 옆을 짧게 치는 크롭은 광대가 더 강조되어 낮게 책정 |
| 하트형 | 이마를 일부 또는 전부 덮는 가르마·댄디·쉐도우가 유리. 이마를 모두 드러내는 깐머리는 낮게 책정 |
| 계란형 | 대부분 소화. 이마를 드러내는 깐머리·가르마를 최상위로 |
| 직사각형 | 옆 볼륨과 곡선감을 더하는 쉐도우 최상위, 세로 길이를 키우는 깐머리는 낮게 |
| 둥근형 | 세로 라인을 만드는 깐머리·크롭(옆 짧게, 위 볼륨) 유리. 앞머리를 내리고 옆 볼륨이 큰 댄디·쉐도우는 낮게 |
| 긴형 | 이마를 덮어 세로 비율을 정돈하는 댄디·쉐도우 최상위, 깐머리는 최하위 |

### 3.2 모질 보정표 — `HAIR_ADJ` (delta, 3항목 합산)

| 입력 조건 | `UP_STYLE` | `PARTED_STYLE` | `DANDY_STYLE` | `CROP_STYLE` | `SHADOW_STYLE` | 근거 |
|---|:-:|:-:|:-:|:-:|:-:|---|
| `hairThickness = THIN` | −1 | 0 | +1 | +1 | +1 | 가는 모발은 넘긴 형태 유지가 어렵고, 내린 앞머리·짧은 질감·펌 볼륨이 유리 |
| `hairThickness = MEDIUM` | 0 | 0 | 0 | 0 | 0 | 보정 없음 |
| `hairThickness = THICK` | +1 | 0 | −1 | +1 | −1 | 굵은 모발은 넘긴 형태 유지가 쉽고, 내리면 무거워 보이기 쉬움 |
| `hairCurl = STRAIGHT` | 0 | 0 | +1 | 0 | −1 | 직모는 일자 앞머리가 고르게 떨어짐, 쉐도우는 펌 의존도가 큼 |
| `hairCurl = SEMI_CURLY` | 0 | +1 | 0 | 0 | +1 | 자연 웨이브가 가르마 볼륨·쉐도우 컬 연출에 유리 |
| `hairCurl = CURLY` | −1 | 0 | −2 | +1 | +2 | 곱슬은 일자 앞머리 연출이 어렵고, 컬을 살리는 쉐도우·짧은 크롭이 유리 |
| `hairDensity = LOW` | −2 | −1 | +1 | +1 | +1 | 이마·가르마 라인을 드러내는 스타일보다 모발이 고르게 덮이는 스타일이 유리 |
| `hairDensity = MEDIUM` | 0 | 0 | 0 | 0 | 0 | 보정 없음 |
| `hairDensity = HIGH` | +1 | +1 | −1 | 0 | −1 | 풍성한 숱은 넘긴 머리·가르마 볼륨이 잘 살고, 내린 머리·펌은 과한 볼륨이 되기 쉬움 |

- 최종 점수 범위: 최소 −3, 최대 9.
- 보정 코드 형식: `ADJ_{THICKNESS|CURL|DENSITY}_{값}_{헤어code}` — 예: `ADJ_CURL_SEMI_CURLY_PARTED_STYLE`

### 3.3 기본 점수 근거 문구 (얼굴형 × 헤어)

**전용 문구** — 기본 점수 4점 이상 조합 (reasonCode: `FACE_{FACESHAPE대문자}_{헤어code}`)

| reasonCode | 문구 |
|---|---|
| `FACE_DIAMOND_PARTED_STYLE` | 가르마로 생기는 옆 볼륨이 관자놀이 라인을 채워, 또렷한 광대 라인과 조화로운 균형을 만듭니다. |
| `FACE_DIAMOND_DANDY_STYLE` | 내린 앞머리가 이마 라인을 부드럽게 감싸 시선을 눈매로 모아 줍니다. |
| `FACE_DIAMOND_SHADOW_STYLE` | 웨이브 볼륨이 이마와 옆머리를 채워 입체적인 얼굴 윤곽을 부드럽게 이어 줍니다. |
| `FACE_HEART_PARTED_STYLE` | 가르마로 이마를 사선으로 나눠, 시원한 이마 라인과 갸름한 턱선이 균형 있게 보입니다. |
| `FACE_HEART_DANDY_STYLE` | 내린 앞머리가 이마 라인을 정돈해 갸름한 턱선이 더 돋보입니다. |
| `FACE_HEART_SHADOW_STYLE` | 앞머리 웨이브가 이마를 부드럽게 덮고 턱 주변까지 자연스러운 볼륨 흐름을 만들어 줍니다. |
| `FACE_OVAL_UP_STYLE` | 균형 잡힌 얼굴 비율이 그대로 드러나 이목구비가 시원하게 보입니다. |
| `FACE_OVAL_PARTED_STYLE` | 어떤 가르마 비율도 잘 소화하는 얼굴형이라 분위기 변화를 주기 좋습니다. |
| `FACE_OVAL_DANDY_STYLE` | 부드러운 윤곽과 내린 앞머리가 어우러져 단정하고 부드러운 인상을 줍니다. |
| `FACE_OVAL_CROP_STYLE` | 짧은 길이에서도 균형 잡힌 얼굴 라인이 살아나 깔끔하고 활동적인 인상을 줍니다. |
| `FACE_OVAL_SHADOW_STYLE` | 자연스러운 웨이브가 부드러운 윤곽과 어울려 트렌디한 무드를 더합니다. |
| `FACE_RECTANGLE_PARTED_STYLE` | 옆 볼륨이 가로 라인을 더해 시원한 세로 라인과 균형을 이룹니다. |
| `FACE_RECTANGLE_DANDY_STYLE` | 내린 앞머리가 세로 비율을 정돈해 또렷한 턱선이 안정감 있게 보입니다. |
| `FACE_RECTANGLE_SHADOW_STYLE` | 부드러운 웨이브가 또렷한 턱선과 대비되어 세련된 인상을 줍니다. |
| `FACE_ROUND_UP_STYLE` | 이마를 드러내 세로 라인이 살아나고, 부드러운 얼굴 윤곽이 더 또렷해 보입니다. |
| `FACE_ROUND_PARTED_STYLE` | 윗머리 볼륨과 가르마의 사선 라인이 시원한 세로 흐름을 만들어 줍니다. |
| `FACE_ROUND_CROP_STYLE` | 옆을 짧게 정리하고 윗머리에 질감을 주면 위로 향하는 산뜻한 실루엣이 완성됩니다. |
| `FACE_LONG_PARTED_STYLE` | 가르마로 이마를 사선으로 나누고 옆 볼륨을 살리면 얼굴 비율이 안정적으로 보입니다. |
| `FACE_LONG_DANDY_STYLE` | 내린 앞머리가 이마 라인을 정돈해 시원한 세로 비율이 부드럽게 정리됩니다. |
| `FACE_LONG_SHADOW_STYLE` | 앞머리와 옆머리의 웨이브 볼륨이 가로 라인을 더해 균형 잡힌 실루엣을 만듭니다. |

**일반 문구** — 기본 점수 3점 이하 조합 (reasonCode: `HAIR_GENERIC_{헤어code}`)

| reasonCode | 문구 |
|---|---|
| `HAIR_GENERIC_UP_STYLE` | 이마를 시원하게 드러내 깔끔하고 자신감 있는 인상을 줍니다. |
| `HAIR_GENERIC_PARTED_STYLE` | 가르마 비율을 조절해 분위기를 쉽게 바꿀 수 있는 스타일입니다. |
| `HAIR_GENERIC_DANDY_STYLE` | 앞머리를 단정하게 내려 부드럽고 깔끔한 인상을 줍니다. |
| `HAIR_GENERIC_CROP_STYLE` | 짧고 정돈된 길이로 관리가 쉽고 활동적인 인상을 줍니다. |
| `HAIR_GENERIC_SHADOW_STYLE` | 자연스러운 웨이브로 부드럽고 트렌디한 무드를 더합니다. |

### 3.4 모질 보정 근거 문구 (양수 보정만)

| reasonCode | 문구 |
|---|---|
| `ADJ_THICKNESS_THIN_DANDY_STYLE` | 가는 모발은 앞머리를 내렸을 때 차분하게 정돈되어 깔끔한 인상을 줍니다. |
| `ADJ_THICKNESS_THIN_CROP_STYLE` | 짧은 길이에 질감을 살리는 크롭은 가는 모발도 볼륨 있어 보이게 합니다. |
| `ADJ_THICKNESS_THIN_SHADOW_STYLE` | 펌의 컬이 가는 모발에 자연스러운 볼륨을 더해 줍니다. |
| `ADJ_THICKNESS_THICK_UP_STYLE` | 굵은 모발은 힘이 좋아 넘긴 형태가 오래 유지됩니다. |
| `ADJ_THICKNESS_THICK_CROP_STYLE` | 짧게 정리하는 크롭은 굵은 모발의 풍성함을 단정하게 살려 줍니다. |
| `ADJ_CURL_STRAIGHT_DANDY_STYLE` | 직모는 내린 앞머리가 고르게 떨어져 단정한 댄디 무드에 잘 맞습니다. |
| `ADJ_CURL_SEMI_CURLY_PARTED_STYLE` | 자연스러운 웨이브가 가르마 라인에 입체감을 더해 줍니다. |
| `ADJ_CURL_SEMI_CURLY_SHADOW_STYLE` | 반곱슬의 결을 살리면 쉐도우 스타일의 컬을 자연스럽게 연출하기 쉽습니다. |
| `ADJ_CURL_CURLY_CROP_STYLE` | 짧은 크롭은 곱슬의 질감을 개성 있는 텍스처로 살려 줍니다. |
| `ADJ_CURL_CURLY_SHADOW_STYLE` | 타고난 컬이 쉐도우 스타일의 부드러운 웨이브와 잘 어울립니다. |
| `ADJ_DENSITY_LOW_DANDY_STYLE` | 앞머리를 자연스럽게 내리는 댄디 스타일은 모발이 고르게 덮여 안정감 있는 실루엣을 만듭니다. |
| `ADJ_DENSITY_LOW_CROP_STYLE` | 짧고 질감 있는 크롭은 모발 밀도와 관계없이 균일한 실루엣을 만들어 줍니다. |
| `ADJ_DENSITY_LOW_SHADOW_STYLE` | 컬이 볼륨을 채워 주어 풍성한 실루엣이 완성됩니다. |
| `ADJ_DENSITY_HIGH_UP_STYLE` | 풍성한 숱 덕분에 넘긴 머리의 볼륨과 형태가 또렷하게 살아납니다. |
| `ADJ_DENSITY_HIGH_PARTED_STYLE` | 풍성한 숱이 가르마 양쪽에 자연스러운 볼륨을 만들어 줍니다. |

### 3.5 계산 예시 (`recommendation-dataset.csv`의 S006)

입력: `faceShape=heart`, `hairThickness=THIN`, `hairCurl=SEMI_CURLY`, `hairDensity=MEDIUM`

| hair | base | THIN | SEMI_CURLY | MEDIUM | score | 순위 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `PARTED_STYLE` | 5 | 0 | +1 | 0 | **6** | **1** (동점 → base 5 > 4) |
| `SHADOW_STYLE` | 4 | +1 | +1 | 0 | **6** | **2** |
| `DANDY_STYLE` | 4 | +1 | 0 | 0 | 5 | 3 |
| `CROP_STYLE` | 3 | +1 | 0 | 0 | 4 | 4 |
| `UP_STYLE` | 2 | −1 | 0 | 0 | 1 | 5 |

- 1위 근거: `FACE_HEART_PARTED_STYLE` + `ADJ_CURL_SEMI_CURLY_PARTED_STYLE`
- 2위 근거: `FACE_HEART_SHADOW_STYLE` + `ADJ_THICKNESS_THIN_SHADOW_STYLE` (THIN·SEMI_CURLY 모두 +1 → `굵기` 우선)

---

## 4. 패션스타일 추천 규칙

### 4.1 기본 점수표 (1차 기준: 체형) — `FASHION_BASE` (0~5점)

| bodyType \ fashion | `CASUAL` 캐주얼 | `DANDY` 댄디 | `MINIMAL` 미니멀 | `CITY_BOY` 시티보이 | `AMEKAJI` 아메카지 | `STREET` 스트릿 | `SPORTY` 스포티 | `GORPCORE` 고프코어 | `VINTAGE` 빈티지 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `standard` 표준유형 | **5** | 4 | **5** | 4 | 3 | 3 | 3 | 3 | 3 |
| `small_inv_triangle` 작은 역삼각형 | 4 | **5** | 4 | 3 | 3 | 3 | **5** | 2 | 3 |
| `large_rectangle` 큰 사각 체형 | 3 | 3 | 2 | 4 | **5** | **5** | 3 | **5** | 3 |
| `rectangle` 사각 체형 | 3 | 3 | 3 | **5** | 4 | 4 | 3 | 3 | **5** |

설계 근거 요약

| 체형 | 방향 |
|---|---|
| 표준유형 | 비율이 평균 범위라 기본 아이템 중심의 캐주얼·미니멀이 가장 안정적, 댄디·시티보이도 무리 없이 소화 |
| 작은 역삼각형 | 슬림한 몸통과 또렷한 어깨선을 드러내는 테일러드(댄디)·기능성 핏(스포티)이 최상위. 부피가 큰 고프코어 아우터는 프레임 대비 과해 보이기 쉬워 최하위 |
| 큰 사각 체형 | 탄탄한 소재와 직선 실루엣의 아메카지·고프코어, 볼륨 있는 스트릿이 최상위. 슬림 핏 중심의 미니멀은 최하위 |
| 사각 체형 | 레이어드로 입체감을 주는 시티보이·빈티지 최상위, 아메카지·스트릿 차상위 |

### 4.2 보조 기준 보정표 — `FASHION_ADJ` (delta, 해당 구간 모두 합산)

| 구간 | `CASUAL` | `DANDY` | `MINIMAL` | `CITY_BOY` | `AMEKAJI` | `STREET` | `SPORTY` | `GORPCORE` | `VINTAGE` |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `BMI_LOW` (< 18.5) | 0 | 0 | 0 | +1 | +1 | 0 | 0 | 0 | +1 |
| `BMI_MID` (18.5~22.9) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `BMI_HIGH` (23.0~24.9) | 0 | +1 | 0 | 0 | 0 | 0 | 0 | +1 | 0 |
| `BMI_VERY_HIGH` (≥ 25.0) | 0 | 0 | −1 | +1 | +1 | 0 | −1 | +1 | 0 |
| `HEIGHT_SHORT` (< 170) | 0 | +1 | +1 | 0 | 0 | 0 | 0 | −1 | 0 |
| `HEIGHT_MID` (170~181) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `HEIGHT_TALL` (≥ 182) | 0 | 0 | 0 | +1 | +1 | 0 | 0 | 0 | 0 |
| `AGE_U22` (≤ 22) | 0 | 0 | 0 | +1 | 0 | +1 | 0 | 0 | 0 |
| `AGE_23_29` (23~29) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `AGE_30S` (30~39) | 0 | +1 | +1 | 0 | 0 | −1 | 0 | 0 | 0 |
| `AGE_40P` (≥ 40) | +1 | +1 | +1 | 0 | 0 | −1 | 0 | 0 | 0 |

- `weightKg`가 `null`이면 BMI 구간 보정을 생략합니다(키·나이 보정만 적용).
- 보정 코드 형식: `ADJ_{구간}_{패션code}` — 예: `ADJ_BMI_HIGH_DANDY`, `ADJ_HEIGHT_SHORT_MINIMAL`, `ADJ_AGE_30S_DANDY`

### 4.3 기본 점수 근거 문구 (체형 × 패션)

**전용 문구** — 기본 점수 4점 이상 조합 (reasonCode: `BODY_{BODYTYPE대문자}_{패션code}`)

| reasonCode | 문구 |
|---|---|
| `BODY_STANDARD_CASUAL` | 어깨·몸통 비율이 고르게 균형 잡혀 기본 아이템만으로도 핏이 깔끔하게 떨어집니다. |
| `BODY_STANDARD_DANDY` | 균형 잡힌 상체 라인이 블레이저와 셔츠의 구조감을 자연스럽게 살려 줍니다. |
| `BODY_STANDARD_MINIMAL` | 균형 잡힌 비율 덕분에 장식 없는 실루엣만으로도 세련된 인상을 줍니다. |
| `BODY_STANDARD_CITY_BOY` | 어떤 핏도 무리 없이 소화하는 비율이라 오버핏 레이어드도 정돈되어 보입니다. |
| `BODY_SMALL_INV_TRIANGLE_CASUAL` | 슬림한 몸통과 또렷한 어깨선 덕분에 기본 셔츠·니트도 핏이 잘 삽니다. |
| `BODY_SMALL_INV_TRIANGLE_DANDY` | 또렷한 어깨선과 슬림한 허리 라인이 테일러드 재킷의 실루엣을 돋보이게 합니다. |
| `BODY_SMALL_INV_TRIANGLE_MINIMAL` | 어깨에서 허리로 이어지는 V라인이 단순한 디자인에서도 선명하게 드러납니다. |
| `BODY_SMALL_INV_TRIANGLE_SPORTY` | 탄탄한 어깨 라인이 트랙 재킷과 기능성 웨어의 활동적인 핏을 살려 줍니다. |
| `BODY_LARGE_RECTANGLE_CITY_BOY` | 여유 있는 오버핏이 듬직한 프레임과 어우러져 편안하고 자연스러운 실루엣을 만듭니다. |
| `BODY_LARGE_RECTANGLE_AMEKAJI` | 듬직한 프레임이 워크 재킷과 데님의 탄탄한 소재감을 멋스럽게 소화합니다. |
| `BODY_LARGE_RECTANGLE_STREET` | 볼륨 있는 후디와 카고 팬츠가 당당한 체격과 어울려 존재감 있는 스타일을 완성합니다. |
| `BODY_LARGE_RECTANGLE_GORPCORE` | 기능성 아우터의 직선 실루엣이 듬직한 체격을 안정감 있게 정리해 줍니다. |
| `BODY_RECTANGLE_CITY_BOY` | 곧은 상체 라인에 오버핏 레이어드를 더하면 자연스러운 입체감이 생깁니다. |
| `BODY_RECTANGLE_AMEKAJI` | 셔츠·베스트·재킷을 겹쳐 입는 레이어드가 직선적인 실루엣에 깊이감을 더합니다. |
| `BODY_RECTANGLE_STREET` | 곧은 체형이 박시한 상의와 와이드 팬츠의 볼륨을 균형 있게 받쳐 줍니다. |
| `BODY_RECTANGLE_VINTAGE` | 코듀로이·니트 베스트 등 질감 있는 소재가 곧은 라인에 따뜻한 포인트를 줍니다. |

**일반 문구** — 기본 점수 3점 이하 조합 (reasonCode: `FASHION_GENERIC_{패션code}`)

| reasonCode | 문구 |
|---|---|
| `FASHION_GENERIC_CASUAL` | 편안하면서도 깔끔한 기본 아이템 위주로 일상에서 활용도가 높습니다. |
| `FASHION_GENERIC_DANDY` | 셔츠와 슬랙스 중심의 단정한 스타일로 깔끔한 인상을 줍니다. |
| `FASHION_GENERIC_MINIMAL` | 장식을 덜어낸 모노톤 실루엣으로 세련된 분위기를 연출합니다. |
| `FASHION_GENERIC_CITY_BOY` | 여유 있는 핏과 레이어드로 편안한 도시 무드를 완성합니다. |
| `FASHION_GENERIC_AMEKAJI` | 워크웨어 소재와 레이어드로 클래식하고 멋스러운 무드를 줍니다. |
| `FASHION_GENERIC_STREET` | 볼륨 있는 실루엣과 그래픽 포인트로 개성 있는 스타일을 완성합니다. |
| `FASHION_GENERIC_SPORTY` | 활동성 좋은 기능성 아이템으로 경쾌한 인상을 줍니다. |
| `FASHION_GENERIC_GORPCORE` | 아웃도어 기능성 아이템을 일상복으로 활용해 실용적인 무드를 연출합니다. |
| `FASHION_GENERIC_VINTAGE` | 질감 있는 소재와 레트로 컬러로 따뜻하고 개성 있는 분위기를 줍니다. |

### 4.4 보조 기준 보정 근거 문구 (양수 보정만)

> BMI·키·나이 수치나 구간명은 문구에 넣지 않습니다.

| reasonCode | 문구 |
|---|---|
| `ADJ_BMI_LOW_CITY_BOY` | 여유 있는 오버핏 레이어드가 자연스러운 볼륨감을 더해 줍니다. |
| `ADJ_BMI_LOW_AMEKAJI` | 셔츠·베스트·재킷 레이어드가 실루엣에 입체감을 더해 줍니다. |
| `ADJ_BMI_LOW_VINTAGE` | 코듀로이·니트 등 도톰한 소재가 따뜻하고 풍성한 실루엣을 만들어 줍니다. |
| `ADJ_BMI_HIGH_DANDY` | 구조감 있는 재킷과 세로 라인이 단정한 실루엣을 만듭니다. |
| `ADJ_BMI_HIGH_GORPCORE` | 직선적인 기능성 아우터가 안정감 있는 핏을 만들어 줍니다. |
| `ADJ_BMI_VERY_HIGH_CITY_BOY` | 여유 있는 스트레이트 핏이 편안하면서도 정돈된 실루엣을 만듭니다. |
| `ADJ_BMI_VERY_HIGH_AMEKAJI` | 탄탄한 소재의 워크웨어가 체격과 어우러져 멋스러운 실루엣을 완성합니다. |
| `ADJ_BMI_VERY_HIGH_GORPCORE` | 직선 실루엣의 아우터가 상체 라인을 깔끔하게 정리해 줍니다. |
| `ADJ_HEIGHT_SHORT_DANDY` | 셔츠부터 슬랙스까지 이어지는 정돈된 라인이 비율을 깔끔하게 살려 줍니다. |
| `ADJ_HEIGHT_SHORT_MINIMAL` | 톤온톤 컬러로 세로 라인이 이어져 전체 비율이 길어 보입니다. |
| `ADJ_HEIGHT_TALL_CITY_BOY` | 큰 키가 오버핏 실루엣을 여유 있게 소화합니다. |
| `ADJ_HEIGHT_TALL_AMEKAJI` | 큰 키가 여러 겹의 레이어드를 답답하지 않게 소화합니다. |
| `ADJ_AGE_U22_CITY_BOY` | 캠퍼스와 일상 어디서나 편하게 입기 좋은 트렌디한 스타일입니다. |
| `ADJ_AGE_U22_STREET` | 또래 사이에서 인기 있는 트렌디한 무드를 자연스럽게 소화할 수 있습니다. |
| `ADJ_AGE_30S_DANDY` | 차분하고 성숙한 무드가 일상과 출근룩 모두에 잘 어울립니다. |
| `ADJ_AGE_30S_MINIMAL` | 절제된 디자인이 깔끔하고 세련된 인상을 오래 유지해 줍니다. |
| `ADJ_AGE_40P_CASUAL` | 편안함과 단정함을 함께 갖춰 일상에서 활용도가 높습니다. |
| `ADJ_AGE_40P_DANDY` | 단정한 셔츠·재킷 조합이 다양한 자리에서 신뢰감 있는 인상을 줍니다. |
| `ADJ_AGE_40P_MINIMAL` | 군더더기 없는 실루엣이 편안하면서도 품위 있는 무드를 줍니다. |

### 4.5 계산 예시 (`recommendation-dataset.csv`의 S006)

입력: `bodyType=small_inv_triangle`, `age=21`(→`AGE_U22`), `heightCm=174`(→`HEIGHT_MID`), `weightKg=63`(BMI 20.8 → `BMI_MID`)

| fashion | base | 보정 합 | score | 순위 |
|---|:-:|:-:|:-:|:-:|
| `DANDY` | 5 | 0 | **5** | **1** (동점 → ordinal 1 < 6) |
| `SPORTY` | 5 | 0 | **5** | **2** |
| `CASUAL` | 4 | 0 | **4** | **3** (동점 → base 4 > 3) |
| `MINIMAL` | 4 | 0 | 4 | 4 (동점 → base 동일, ordinal 2 > 0) |
| `CITY_BOY` | 3 | +1 (`AGE_U22`) | 4 | 5 |
| `STREET` | 3 | +1 (`AGE_U22`) | 4 | 6 |
| 나머지 | 2~3 | 0 | 2~3 | 7~9 |

- 1~3위 모두 양수 보정이 없으므로 근거는 각 1문장(`BODY_SMALL_INV_TRIANGLE_DANDY`, `BODY_SMALL_INV_TRIANGLE_SPORTY`, `BODY_SMALL_INV_TRIANGLE_CASUAL`).

---

## 5. 맞춤 유형 코드 체계 (`type_code`) — 이름 미확정 대응

### 5.1 설계 원칙

1. **코드와 텍스트 완전 분리**: 백엔드 로직·DB·API·프론트 분기는 `typeCode`(예: `TYPE-08`)만 사용합니다. 유형명·설명·스타일 포인트는 시드 파일(JSON) → DB `type_definition` 테이블에서만 읽습니다. (R-07)
2. **팀 확정 기준: 체형 4종 × 얼굴형 6종 = 24개 유형** (`TYPE-01` ~ `TYPE-24`). 얼굴형·체형을 그룹으로 묶지 않습니다.
   ```text
   typeIndex = bodyIndex × 6 + faceIndex + 1
   bodyIndex : standard=0, small_inv_triangle=1, large_rectangle=2, rectangle=3
   faceIndex : diamond=0, heart=1, oval=2, rectangle=3, round=4, long=5
   typeCode  = "TYPE-" + 2자리 0채움(typeIndex)       # TYPE-01 ~ TYPE-24
   ```
3. 기능정의서 ‘패션 타입 정의(팀 작성)’ 시트의 `TYPE-01 ~ TYPE-12`는 초기 초안이며 폐기합니다. 팀 회의 결과는 시트를 24행으로 늘려 5.2 표와 같은 코드로 기입합니다. 시트의 `분류 축`은 모두 **결합(신체: 체형×얼굴형)**입니다.
4. 취향 기반 ‘패션 타입’(F-05 스와이프 검사)은 2차 목표에서 **별도 축** `fashionTypeCode`로 추가하며, `typeCode` 체계는 바꾸지 않습니다. (R-14)
5. 얼굴형·체형 Key 순서(1.1, 1.3 표)는 `typeCode`를 결정하므로 **순서 변경 금지**입니다.

코드 배치표

| bodyType \ faceShape | `diamond` 다이아몬드형 | `heart` 하트형 | `oval` 계란형 | `rectangle` 직사각형 | `round` 둥근형 | `long` 긴형 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `standard` 표준유형 | `TYPE-01` | `TYPE-02` | `TYPE-03` | `TYPE-04` | `TYPE-05` | `TYPE-06` |
| `small_inv_triangle` 작은 역삼각형 | `TYPE-07` | `TYPE-08` | `TYPE-09` | `TYPE-10` | `TYPE-11` | `TYPE-12` |
| `large_rectangle` 큰 사각 체형 | `TYPE-13` | `TYPE-14` | `TYPE-15` | `TYPE-16` | `TYPE-17` | `TYPE-18` |
| `rectangle` 사각 체형 | `TYPE-19` | `TYPE-20` | `TYPE-21` | `TYPE-22` | `TYPE-23` | `TYPE-24` |

### 5.2 유형 매핑표 (현재 시드 값)

`typeName`은 전부 **가칭**(`nameConfirmed: false`)입니다. 가칭은 부록 A의 ‘A안: 키워드 조합형’ 규칙(`{체형 키워드} {얼굴형 키워드}`)으로 만든 임시값이고, `description`·`stylePoints`는 체형별 패션 방향(4장)과 얼굴형별 헤어 방향(3장)을 조합해 만든 초안입니다.

| typeCode | bodyType | faceShape | 가칭 `typeName` | `description` | `stylePoints` |
|---|---|---|---|---|---|
| `TYPE-01` | `standard` 표준유형 | `diamond` 다이아몬드형 | 밸런스 샤프 | 균형 잡힌 체형과 입체적인 광대 라인이 어우러져, 캐주얼·미니멀처럼 기본에 충실한 스타일이 잘 어울리는 유형입니다. | ① 레귤러 핏 기본 아이템과 모노톤 컬러로 균형 잡힌 비율 살리기 ② 옆 볼륨이 있는 가르마 헤어로 관자놀이 라인 채우기 |
| `TYPE-02` | `standard` 표준유형 | `heart` 하트형 | 밸런스 프레시 | 균형 잡힌 체형과 시원한 이마에서 갸름하게 모이는 턱선이 어우러져, 캐주얼·미니멀처럼 기본에 충실한 스타일이 잘 어울리는 유형입니다. | ① 레귤러 핏 기본 아이템과 모노톤 컬러로 균형 잡힌 비율 살리기 ② 가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기 |
| `TYPE-03` | `standard` 표준유형 | `oval` 계란형 | 밸런스 내추럴 | 균형 잡힌 체형과 고른 얼굴 비율이 어우러져, 캐주얼·미니멀처럼 기본에 충실한 스타일이 잘 어울리는 유형입니다. | ① 레귤러 핏 기본 아이템과 모노톤 컬러로 균형 잡힌 비율 살리기 ② 깐머리·가르마처럼 이마를 드러내는 헤어로 고른 비율 살리기 |
| `TYPE-04` | `standard` 표준유형 | `rectangle` 직사각형 | 밸런스 클리어 | 균형 잡힌 체형과 선명한 턱선의 시원한 세로 비율이 어우러져, 캐주얼·미니멀처럼 기본에 충실한 스타일이 잘 어울리는 유형입니다. | ① 레귤러 핏 기본 아이템과 모노톤 컬러로 균형 잡힌 비율 살리기 ② 웨이브를 준 쉐도우 헤어로 또렷한 턱선에 부드러움 더하기 |
| `TYPE-05` | `standard` 표준유형 | `round` 둥근형 | 밸런스 소프트 | 균형 잡힌 체형과 부드러운 곡선의 얼굴 윤곽이 어우러져, 캐주얼·미니멀처럼 기본에 충실한 스타일이 잘 어울리는 유형입니다. | ① 레귤러 핏 기본 아이템과 모노톤 컬러로 균형 잡힌 비율 살리기 ② 윗머리 볼륨을 살린 깐머리·크롭 헤어로 세로 흐름 만들기 |
| `TYPE-06` | `standard` 표준유형 | `long` 긴형 | 밸런스 시크 | 균형 잡힌 체형과 시원하게 뻗은 세로 라인이 어우러져, 캐주얼·미니멀처럼 기본에 충실한 스타일이 잘 어울리는 유형입니다. | ① 레귤러 핏 기본 아이템과 모노톤 컬러로 균형 잡힌 비율 살리기 ② 앞머리를 내린 댄디·쉐도우 헤어로 세로 비율 정돈하기 |
| `TYPE-07` | `small_inv_triangle` 작은 역삼각형 | `diamond` 다이아몬드형 | V라인 샤프 | 또렷한 어깨선의 슬림한 체형과 입체적인 광대 라인이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조 ② 옆 볼륨이 있는 가르마 헤어로 관자놀이 라인 채우기 |
| `TYPE-08` | `small_inv_triangle` 작은 역삼각형 | `heart` 하트형 | V라인 프레시 | 또렷한 어깨선의 슬림한 체형과 시원한 이마에서 갸름하게 모이는 턱선이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조 ② 가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기 |
| `TYPE-09` | `small_inv_triangle` 작은 역삼각형 | `oval` 계란형 | V라인 내추럴 | 또렷한 어깨선의 슬림한 체형과 고른 얼굴 비율이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조 ② 깐머리·가르마처럼 이마를 드러내는 헤어로 고른 비율 살리기 |
| `TYPE-10` | `small_inv_triangle` 작은 역삼각형 | `rectangle` 직사각형 | V라인 클리어 | 또렷한 어깨선의 슬림한 체형과 선명한 턱선의 시원한 세로 비율이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조 ② 웨이브를 준 쉐도우 헤어로 또렷한 턱선에 부드러움 더하기 |
| `TYPE-11` | `small_inv_triangle` 작은 역삼각형 | `round` 둥근형 | V라인 소프트 | 또렷한 어깨선의 슬림한 체형과 부드러운 곡선의 얼굴 윤곽이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조 ② 윗머리 볼륨을 살린 깐머리·크롭 헤어로 세로 흐름 만들기 |
| `TYPE-12` | `small_inv_triangle` 작은 역삼각형 | `long` 긴형 | V라인 시크 | 또렷한 어깨선의 슬림한 체형과 시원하게 뻗은 세로 라인이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조 ② 앞머리를 내린 댄디·쉐도우 헤어로 세로 비율 정돈하기 |
| `TYPE-13` | `large_rectangle` 큰 사각 체형 | `diamond` 다이아몬드형 | 볼드 샤프 | 듬직한 프레임과 입체적인 광대 라인이 어우러져, 아메카지·고프코어·스트릿처럼 소재감과 볼륨이 있는 스타일이 잘 어울리는 유형입니다. | ① 워크 재킷·기능성 아우터처럼 직선 실루엣 아이템으로 안정감 있는 핏 연출 ② 옆 볼륨이 있는 가르마 헤어로 관자놀이 라인 채우기 |
| `TYPE-14` | `large_rectangle` 큰 사각 체형 | `heart` 하트형 | 볼드 프레시 | 듬직한 프레임과 시원한 이마에서 갸름하게 모이는 턱선이 어우러져, 아메카지·고프코어·스트릿처럼 소재감과 볼륨이 있는 스타일이 잘 어울리는 유형입니다. | ① 워크 재킷·기능성 아우터처럼 직선 실루엣 아이템으로 안정감 있는 핏 연출 ② 가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기 |
| `TYPE-15` | `large_rectangle` 큰 사각 체형 | `oval` 계란형 | 볼드 내추럴 | 듬직한 프레임과 고른 얼굴 비율이 어우러져, 아메카지·고프코어·스트릿처럼 소재감과 볼륨이 있는 스타일이 잘 어울리는 유형입니다. | ① 워크 재킷·기능성 아우터처럼 직선 실루엣 아이템으로 안정감 있는 핏 연출 ② 깐머리·가르마처럼 이마를 드러내는 헤어로 고른 비율 살리기 |
| `TYPE-16` | `large_rectangle` 큰 사각 체형 | `rectangle` 직사각형 | 볼드 클리어 | 듬직한 프레임과 선명한 턱선의 시원한 세로 비율이 어우러져, 아메카지·고프코어·스트릿처럼 소재감과 볼륨이 있는 스타일이 잘 어울리는 유형입니다. | ① 워크 재킷·기능성 아우터처럼 직선 실루엣 아이템으로 안정감 있는 핏 연출 ② 웨이브를 준 쉐도우 헤어로 또렷한 턱선에 부드러움 더하기 |
| `TYPE-17` | `large_rectangle` 큰 사각 체형 | `round` 둥근형 | 볼드 소프트 | 듬직한 프레임과 부드러운 곡선의 얼굴 윤곽이 어우러져, 아메카지·고프코어·스트릿처럼 소재감과 볼륨이 있는 스타일이 잘 어울리는 유형입니다. | ① 워크 재킷·기능성 아우터처럼 직선 실루엣 아이템으로 안정감 있는 핏 연출 ② 윗머리 볼륨을 살린 깐머리·크롭 헤어로 세로 흐름 만들기 |
| `TYPE-18` | `large_rectangle` 큰 사각 체형 | `long` 긴형 | 볼드 시크 | 듬직한 프레임과 시원하게 뻗은 세로 라인이 어우러져, 아메카지·고프코어·스트릿처럼 소재감과 볼륨이 있는 스타일이 잘 어울리는 유형입니다. | ① 워크 재킷·기능성 아우터처럼 직선 실루엣 아이템으로 안정감 있는 핏 연출 ② 앞머리를 내린 댄디·쉐도우 헤어로 세로 비율 정돈하기 |
| `TYPE-19` | `rectangle` 사각 체형 | `diamond` 다이아몬드형 | 스트레이트 샤프 | 곧게 떨어지는 상체 라인과 입체적인 광대 라인이 어우러져, 시티보이·빈티지처럼 레이어드와 질감이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 오버핏 셔츠 위 니트·베스트 레이어드로 입체감 더하기 ② 옆 볼륨이 있는 가르마 헤어로 관자놀이 라인 채우기 |
| `TYPE-20` | `rectangle` 사각 체형 | `heart` 하트형 | 스트레이트 프레시 | 곧게 떨어지는 상체 라인과 시원한 이마에서 갸름하게 모이는 턱선이 어우러져, 시티보이·빈티지처럼 레이어드와 질감이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 오버핏 셔츠 위 니트·베스트 레이어드로 입체감 더하기 ② 가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기 |
| `TYPE-21` | `rectangle` 사각 체형 | `oval` 계란형 | 스트레이트 내추럴 | 곧게 떨어지는 상체 라인과 고른 얼굴 비율이 어우러져, 시티보이·빈티지처럼 레이어드와 질감이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 오버핏 셔츠 위 니트·베스트 레이어드로 입체감 더하기 ② 깐머리·가르마처럼 이마를 드러내는 헤어로 고른 비율 살리기 |
| `TYPE-22` | `rectangle` 사각 체형 | `rectangle` 직사각형 | 스트레이트 클리어 | 곧게 떨어지는 상체 라인과 선명한 턱선의 시원한 세로 비율이 어우러져, 시티보이·빈티지처럼 레이어드와 질감이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 오버핏 셔츠 위 니트·베스트 레이어드로 입체감 더하기 ② 웨이브를 준 쉐도우 헤어로 또렷한 턱선에 부드러움 더하기 |
| `TYPE-23` | `rectangle` 사각 체형 | `round` 둥근형 | 스트레이트 소프트 | 곧게 떨어지는 상체 라인과 부드러운 곡선의 얼굴 윤곽이 어우러져, 시티보이·빈티지처럼 레이어드와 질감이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 오버핏 셔츠 위 니트·베스트 레이어드로 입체감 더하기 ② 윗머리 볼륨을 살린 깐머리·크롭 헤어로 세로 흐름 만들기 |
| `TYPE-24` | `rectangle` 사각 체형 | `long` 긴형 | 스트레이트 시크 | 곧게 떨어지는 상체 라인과 시원하게 뻗은 세로 라인이 어우러져, 시티보이·빈티지처럼 레이어드와 질감이 살아나는 스타일이 잘 어울리는 유형입니다. | ① 오버핏 셔츠 위 니트·베스트 레이어드로 입체감 더하기 ② 앞머리를 내린 댄디·쉐도우 헤어로 세로 비율 정돈하기 |

결과 화면 헤드라인: `headlineTemplate = "당신은 {typeName} 유형입니다"` → 예: `당신은 V라인 프레시 유형입니다`. 이 템플릿도 시드 파일에 있으며 백엔드가 완성된 문자열(`headline`)로 내려줍니다.

### 5.3 시드 파일 분리 방식

| 파일 (경로: `backend/src/main/resources/seed/`) | 내용 | 적재 대상 테이블 (Step 2 확정) |
|---|---|---|
| `type-definitions.json` | 헤드라인 템플릿, 유형 24종 (코드·체형·얼굴형·유형명·확정 여부·설명·스타일 포인트) | `type_definition` |
| `catalog.json` | 얼굴형 6종·체형 4종 라벨과 상세 설명(6장), 헤어 5종·패션 9종 이름·정의·아이템 키워드·imageKey | `face_shape_def`, `body_type_def`, `hair_style`, `fashion_style` |
| `hair-rules.json` | 3.1 기본 점수표, 3.2 보정표, 3.3·3.4 근거 문구 | `rule_score`, `rule_reason` |
| `fashion-rules.json` | 4.1 기본 점수표, 4.2 보정표, 4.3·4.4 근거 문구 | `rule_score`, `rule_reason` |
| `analysis-labels.json` | AI 경고 코드별 문구·심각도, features·Size Korea 항목 라벨 (`api-spec.md` 부록 A) | `analysis_label` |

`type-definitions.json` 구조 (1개 항목만 예시, 실제 파일은 5.2 표의 24개 전체):

```json
{
  "ruleVersion": "v1.1-draft",
  "headlineTemplate": "당신은 {typeName} 유형입니다",
  "types": [
    {
      "typeCode": "TYPE-08",
      "bodyType": "small_inv_triangle",
      "faceShape": "heart",
      "typeName": "V라인 프레시",
      "nameConfirmed": false,
      "description": "또렷한 어깨선의 슬림한 체형과 시원한 이마에서 갸름하게 모이는 턱선이 어우러져, 댄디·스포티처럼 핏이 살아나는 스타일이 잘 어울리는 유형입니다.",
      "stylePoints": [
        "테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조",
        "가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기"
      ],
      "sortOrder": 8
    }
  ]
}
```

필드 규칙

| 필드 | 타입 | 규칙 |
|---|---|---|
| `typeCode` | string | `TYPE-01` ~ `TYPE-24`, 중복 불가 |
| `bodyType` | string | 체형 Key 4종 |
| `faceShape` | string | 얼굴형 Key 6종 |
| `typeName` | string | 화면 표시 유형명 (1~20자) |
| `nameConfirmed` | boolean | 팀 확정 이름이면 `true` (API 응답 필드명도 동일하게 `nameConfirmed`) |
| `description` | string | 1~2문장, 7장 표현 가이드 준수 |
| `stylePoints` | string[] | 2개 이상 |
| `sortOrder` | int | 1 ~ 24 |

적재 규칙

1. 서버 기동 시 `SeedLoader`가 각 JSON을 읽어 **코드 기준 upsert** (`moda.seed.mode: upsert` | `none`). 코드가 같으면 텍스트만 갱신되므로 세션 데이터에는 영향이 없습니다.
2. JSON 검증 실패 시 **서버 기동을 중단**합니다(잘못된 문구로 시연되는 상황 방지). 검증 항목: 유형 24개·얼굴형 6개·체형 4개·헤어 5개·패션 9개 개수 일치, `(bodyType, faceShape)` 24개 조합이 빠짐없이 1번씩 존재, 각 `typeCode`가 5.1 공식 결과와 일치, 알 수 없는 Key 없음.
3. 유형명 확정 후 교체 절차:
   1. `type-definitions.json`의 `typeName` 24개 수정, `nameConfirmed: true`로 변경
   2. 필요 시 `description`, `stylePoints`, `headlineTemplate` 수정
   3. `ruleVersion`을 올리고 커밋 → 서버 재시작
   4. **프론트엔드 코드 변경 없음** (API 응답 텍스트가 자동 반영)
   5. 기능정의서 ‘패션 타입 정의(팀 작성)’ 시트에도 같은 값을 기입

### 5.4 유형 결과 화면 표시 규칙 (프론트엔드)

| 표시 요소 | 사용할 응답 필드 (`GET /result` → `data.type`) |
|---|---|
| 큰 제목 “당신은 ~ 유형입니다” | `headline` (그대로 출력) |
| 유형 배지/칩 | `typeName` |
| 설명 문단 | `description` |
| 스타일 포인트 리스트 | `stylePoints[]` |
| “가칭” 표시(선택) | `nameConfirmed === false`일 때 개발 빌드에서만 작은 배지 노출 권장, 대회 빌드에서는 숨김 |

---

## 6. [분석 상세보기] 표시 문구

### 6.1 얼굴형 설명 (`analysis-detail` → `face.description`)

| Key | 문구 |
|---|---|
| `diamond` | 광대 라인이 또렷하고 이마와 턱이 갸름해 입체감이 돋보이는 얼굴형입니다. |
| `heart` | 이마 라인이 시원하고 턱선이 갸름하게 모여 산뜻한 인상을 주는 얼굴형입니다. |
| `oval` | 이마·광대·턱의 비율이 고르게 이어져 다양한 스타일을 소화하기 좋은 얼굴형입니다. |
| `rectangle` | 세로 비율이 시원하고 턱선이 또렷해 선명하고 신뢰감 있는 인상을 주는 얼굴형입니다. |
| `round` | 곡선이 부드럽게 이어져 친근하고 편안한 인상을 주는 얼굴형입니다. |
| `long` | 세로 라인이 시원하게 뻗어 차분하고 세련된 인상을 주는 얼굴형입니다. |

### 6.2 체형 설명 (`analysis-detail` → `body.description`)

| Key | 문구 |
|---|---|
| `standard` | 어깨·허리·엉덩이 비율이 키 대비 평균 범위 안에 고르게 있어 다양한 핏을 소화하기 좋은 체형입니다. |
| `small_inv_triangle` | 몸통이 슬림하고 어깨선이 상대적으로 또렷해 V라인 실루엣이 살아나는 체형입니다. |
| `large_rectangle` | 어깨부터 허리까지 몸통이 탄탄하게 이어져 듬직한 실루엣을 가진 체형입니다. |
| `rectangle` | 어깨·허리·엉덩이 너비가 고르게 이어져 곧고 안정적인 실루엣을 가진 체형입니다. |

### 6.3 측정 항목(features / Size Korea) 라벨

`api-spec.md` 부록 A.4에 정의합니다(AI 응답 키 ↔ 화면 라벨).

---

## 7. 표현 가이드 (R-11: 외모 평가 배제, 장점 중심)

| 구분 | 원칙 |
|---|---|
| 기본 | 결점 지적이 아니라 “어울리는 스타일”과 “살아나는 장점”으로 표현 |
| 금지 표현 | 단점, 결점, 커버, 가리다, 보완하다, 콤플렉스, 뚱뚱, 마른, 왜소, 작은 키, 큰 얼굴, 넓은 얼굴, 짧은 다리, 비만, 저체중, 과체중, 숱이 적어서, 탈모, 못생긴, 평균 이하 |
| 대체 표현 | 각진 → 또렷한 / 넓은 이마 → 시원한 이마 라인 / 덩치가 큰 → 듬직한 프레임 / 마른 → 슬림한 / 숱이 적은 → (언급하지 않고 스타일 효과로 설명) |
| 수치 노출 | BMI·몸무게는 어떤 화면에도 노출 금지. Size Korea 비교 수치는 [분석 상세보기]에서만 “평균과 비슷 / 평균보다 n% 큼 / 평균보다 n% 작음” 같은 중립 문구로 표시 |
| 신뢰도 표기 | `confidence`는 “분석 확신도”로 표기, 낮을 때는 “여러 얼굴형의 특징이 함께 보여요”처럼 중립적으로 안내 |
| 면책 | 결과 화면 하단 고정 문구 권장: “분석 결과는 스타일 참고용이며, 언제든 [분석 상세보기]에서 직접 수정할 수 있어요.” |

---

## 8. 수동 보정·재산출 규칙 (F-03-04, F-04-03)

| 상황 | 처리 |
|---|---|
| `PATCH /profile`로 `faceShape` 변경 | 얼굴형 `source=MANUAL`로 저장(AI 원본은 `aiResult`로 보존) → `typeCode`·헤어 추천 재산출 |
| `PATCH /profile`로 `bodyType` 변경 | 체형 `source=MANUAL` → `typeCode`·패션 추천 재산출 |
| `PATCH /profile`로 모질 3항목 변경 | 헤어 추천만 재산출 (`typeCode` 불변) |
| `POST /profile-input` 재호출로 나이·몸무게 변경 | 패션 추천만 재산출 |
| `POST /profile-input` 재호출로 `heightCm` 변경 | 기존 체형 분석은 키 기준이 달라지므로 **AI 체형 결과 초기화** (`bodyAnalysisReset: true`) → 전신 사진 재분석 필요 |
| 얼굴/전신 재촬영 후 분석 성공 | 새 AI 결과로 교체, 해당 항목 `source=AI` |
| AI가 `usable=false` 반환 (분석 불가 사진) | 경고 메시지(`warnings[].message`)와 재촬영 안내만 표시합니다. 이전 결과·`progress`는 바뀌지 않으며, **정상 분석(`usable=true`)이 완료되어야만 다음 단계로 이동**합니다. 실패 횟수와 관계없이 “직접 선택하기”를 자동으로 노출하지 않습니다. |
| AI 서버 장애 (`503 AI_SERVER_UNAVAILABLE` / `504 AI_TIMEOUT`) | 재시도 버튼과 함께 “직접 선택하기”를 노출하고 `PATCH /profile`로 얼굴형·체형을 수동 설정할 수 있습니다(`source=MANUAL`, AI 결과 없이도 결과 산출 가능). |
| 결과 확인 후 [분석 상세보기]에서 AI 판정 수정 | `PATCH /profile`로 언제든 수정 가능 (위 `faceShape`·`bodyType` 변경 규칙 적용) |

---

## 9. 데이터셋 스키마 (`docs/recommendation-dataset.csv`)

AI 팀 공유용. 현재 샘플 10행의 출력 라벨은 **이 문서의 규칙 엔진(v1.1-draft)으로 계산한 정답값**입니다. 향후 모델 학습·검증 데이터로 확장할 때 같은 헤더를 유지합니다.

| 컬럼 | 구분 | 타입 | 허용 값 / 범위 | null |
|---|---|---|---|---|
| `sample_id` | 식별 | string | `S001` 형식 | 불가 |
| `age` | 입력 | int | 14 ~ 80 | 불가 |
| `height_cm` | 입력 | int | 140 ~ 210 | 불가 |
| `weight_kg` | 입력 | int | 30 ~ 200 | 가능(빈 칸) |
| `bmi` | 파생 | decimal(1) | `weight_kg / (height_cm/100)^2` | `weight_kg` 빈 칸이면 빈 칸 |
| `hair_thickness` | 입력 | enum | `THIN` / `MEDIUM` / `THICK` | 불가 |
| `hair_curl` | 입력 | enum | `STRAIGHT` / `SEMI_CURLY` / `CURLY` | 불가 |
| `hair_density` | 입력 | enum | `LOW` / `MEDIUM` / `HIGH` | 불가 |
| `face_shape` | 입력 (AI 출력 또는 수동) | enum | 1.1의 6개 Key | 불가 |
| `body_type` | 입력 (AI 출력 또는 수동) | enum | 1.3의 4개 Key | 불가 |
| `type_code` | 출력 | string | `TYPE-01` ~ `TYPE-24` (5.1 공식) | 불가 |
| `hair_rec_1`, `hair_rec_2` | 출력 | enum | 1.5의 헤어 code | 불가 |
| `fashion_rec_1` ~ `fashion_rec_3` | 출력 | enum | 1.6의 패션 code | 불가 |
| `rule_version` | 메타 | string | 라벨 생성 규칙 버전 | 불가 |
| `label_source` | 메타 | enum | `RULE_ENGINE` (규칙 산출) / `HUMAN` (팀 수동 라벨) / `USER_FEEDBACK` (2차: 사용자 피드백) | 불가 |

- 인코딩 UTF-8(BOM 없음), 구분자 쉼표, 줄바꿈 LF.
- 얼굴·전신 **사진 파일, 사진 경로, 얼굴 랜드마크 원본 좌표는 이 데이터셋에 넣지 않습니다.** (R-01)

---

## 10. 미확정·검토 항목

| ID | 항목 | 담당 | 기한 | 현재 처리 |
|---|---|---|---|---|
| Q-01 | 맞춤 유형명 24종 확정 (부록 A 참고) | 팀 전체 | 팀 회의 | 가칭 사용, 시드 파일만 교체하면 반영 |
| Q-02 | 체형 분석 유료 API 선정 | 김민건·최기완 | Step 4 전 | 필수 응답은 `usable`·`body_type`·`warnings`만, 부가 정보는 선택 (`api-spec.md` 부록 A.4) |
| Q-03 | AI 얼굴형·체형 판정 임계값 calibrate | 김민건·최기완 | Step 4 전 | Key 고정, 수치만 변경 |
| Q-04 | 헤어·패션 예시 이미지 확보(`imageKey` 패션 9종 + 헤어 5종 (총 14장)) | 이상현·팀 | 11/27 | 출처 확인 가능한 이미지만 (R-13) |
| Q-05 | 점수표 체감 검증(팀원 4명 + 지인 테스트 결과로 미세 조정) | 박준희 | 11/26 | v1.1-draft |

---

## 부록 A. 팀 회의용 유형 네이밍 컨셉 초안 (참고용, 확정 아님)

선정 기준: ① 외모 평가로 읽히지 않을 것(R-11) ② 공유 카드에 들어갈 만큼 짧을 것(한글 2~8자 권장) ③ 24종이 서로 헷갈리지 않을 것 ④ 24개를 일일이 짓지 않도록 **체형 키워드 4개 + 얼굴형 키워드 6개 = 단어 10개**만 정하면 조합으로 완성되는 방식 권장

### A안. 키워드 조합형 (현재 가칭 방식)

`{체형 키워드} {얼굴형 키워드}` — 체형: `standard`=밸런스 · `small_inv_triangle`=V라인 · `large_rectangle`=볼드 · `rectangle`=스트레이트 / 얼굴형: `diamond`=샤프 · `heart`=프레시 · `oval`=내추럴 · `rectangle`=클리어 · `round`=소프트 · `long`=시크

예: `TYPE-08` 작은 역삼각형 × 하트형 → **V라인 프레시** / `TYPE-17` 큰 사각 체형 × 둥근형 → **볼드 소프트**

- 장점: 규칙이 명확해 추천 근거를 설명하기 쉬움 / 단점: 감성적 임팩트가 약함

### B안. 무드 페르소나형

`{얼굴형 수식어} {체형 페르소나}` — 체형: `standard`=올라운더 · `small_inv_triangle`=젠틀맨 · `large_rectangle`=탐험가 · `rectangle`=큐레이터 / 얼굴형: `diamond`=세련된 · `heart`=산뜻한 · `oval`=내추럴 · `rectangle`=클래식 · `round`=다정한 · `long`=모던

예: `TYPE-08` → **산뜻한 젠틀맨** / `TYPE-17` → **다정한 탐험가** / `TYPE-22` → **클래식 큐레이터**

- 장점: 결과 공유(F-13) 시 화제성 / 단점: 체형·얼굴형 근거가 이름에서 직접 드러나지 않음

### C안. 이니셜 코드형 (MBTI식, A·B안과 병행 가능)

`{체형 이니셜}{얼굴형 이니셜}` — 체형: B(Balance)·V(V-line)·F(Frame)·S(Straight) / 얼굴형: D(Diamond)·H(Heart)·O(Oval)·R(Rectangle)·C(Circle=round)·L(Long)

예: `TYPE-08` → **VH** / `TYPE-17` → **FC** / “당신은 **VH · 산뜻한 젠틀맨** 유형입니다”처럼 코드 + 별칭 병행

- 장점: 짧고 공유하기 쉬움, 24종 코드 체계와 1:1 대응 / 단점: 단독 사용 시 의미 전달이 약함

> 회의에서 정할 것: (1) 컨셉 1개 선택 또는 C안 + 다른 안 병행 여부 (2) 체형 키워드 4개·얼굴형 키워드 6개 (3) 24개 `description`·`stylePoints` 수정 여부 → 결과를 5.3 교체 절차대로 반영
