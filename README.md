# moda-app
Moda : 남성 전용 체형, 얼굴형 분석 -> 헤어, 패션 스타일 추천 APP

## 빠른 시작 (클론 후)

1. 처음 한 번 설치: [Java 21](https://adoptium.net), [Node.js](https://nodejs.org), 폰으로 볼 거면 폰에 **Expo Go**
2. 실행: **Windows는 `start.cmd` 더블클릭**, macOS·Linux는 `./start.sh`
   - 처음 실행 때만 관리자 권한 창이 뜨면 **예** (폰이 PC 서버에 접속하도록 방화벽 허용)
3. 브라우저는 `w`, 폰은 PC와 **같은 Wi-Fi**에서 Expo Go로 QR 스캔

DB·이미지·서버 주소는 모두 자동입니다. 안 될 때는 [mobile/README.md](mobile/README.md) 참고.

## 스타일 이미지와 DB

| 폴더 | 내용 |
|------|------|
| `hair_images/{헤어코드}/` | 헤어 사진 원본 |
| `style/{스타일}/{세트}/` | 코디 전신(`full_shot.png`)과 아이템 사진 원본, `manifest.json` |
| `sql/` | DB 스키마·마스터 데이터·이미지 경로 적재 SQL. DB의 경로는 위 두 폴더 기준 상대경로 |

- 기본 실행(`cd backend; ./gradlew bootRun`, H2)은 기동할 때마다 `sql/`을 자동 적재하고, 이미지 파일을 `/images/hair/**`, `/images/style/**`로 서빙합니다. 앱은 `GET /api/v1/style-images`로 경로를 받아 표시합니다.
- MySQL은 자동 적재하지 않으니 직접 실행합니다: `schema_mysql.sql` → `seed_master_data.sql` → `load_hair_images.sql` → `load_outfits.sql` (확인: `verify_load.sql`).
- 백엔드는 `backend/`에서 실행해야 합니다 (`../sql`, `../hair_images`, `../style` 기준).
