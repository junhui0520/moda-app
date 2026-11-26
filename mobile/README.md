# Moda Mobile (Expo)

스타일 이미지(결과 카드의 큰 사진 + 아이템 작은 사진, 챗봇 썸네일)는 백엔드 `GET /api/v1/style-images`에서 `imageKey`로 찾아 표시합니다 ([useStyleImages.ts](src/hooks/useStyleImages.ts), [StyleImageCard.tsx](src/components/StyleImageCard.tsx)). 이미지가 없으면 기존 placeholder를 보여줍니다.

## 실행

루트의 `start.cmd`(Windows) / `start.sh`(macOS·Linux)가 백엔드와 앱을 함께 켜고, **로컬 백엔드 주소를 자동 설정**합니다 (`EXPO_PUBLIC_USE_LOCAL_API=1`).
직접 켤 때는 아래처럼 합니다.

```powershell
cd backend; .\gradlew bootRun                                         # 터미널 1
cd mobile; npm install; $env:EXPO_PUBLIC_USE_LOCAL_API="1"; npx expo start   # 터미널 2 (w = 브라우저, QR = Expo Go)
```

- 서버 주소 우선순위: `EXPO_PUBLIC_API_BASE_URL` > (`USE_LOCAL_API=1`일 때) Expo 개발 서버의 PC IP:8080 > 배포 서버(Railway). 앱의 설정 화면에서도 바꿀 수 있습니다.
- 배포 서버(Railway)에는 이미지 폴더가 없어 스타일 이미지가 placeholder로 나옵니다. 이미지를 보려면 로컬 백엔드로 연결하세요.
- 폰과 PC는 같은 Wi-Fi여야 하고, 처음 백엔드를 켤 때 Windows 방화벽 팝업이 뜨면 **허용**을 누르세요.
- 폰에서는 **Expo Go**를 설치하고 QR을 스캔합니다 (iPhone은 카메라 앱, Android는 Expo Go 안에서).
- 안 되면: 폰 브라우저에서 `http://<PC IP>:8080/api/v1/health`가 열리는지 확인 → 안 열리면 방화벽 문제 (관리자 PowerShell: `New-NetFirewallRule -DisplayName moda-8080 -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow`).

## 챗봇을 로컬 LLM(Ollama)으로

백엔드를 아래 환경변수로 실행하면 됩니다 (기본은 템플릿 답변 Mock).

```powershell
$env:MODA_CHAT_MODE="external"
$env:MODA_CHAT_BASE_URL="http://localhost:11434/v1"
$env:MODA_CHAT_MODEL="qwen2.5:7b"
$env:MODA_CHAT_API_KEY="ollama"   # Ollama는 키를 안 보지만 비어 있으면 경고 로그
.\gradlew bootRun
```
