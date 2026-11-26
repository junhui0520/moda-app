# Moda Mobile (Expo)

결과 화면 + 챗봇 화면. 기본 정보 입력·얼굴·체형 분석은 아직 없고 [src/randomDemo.ts](src/randomDemo.ts)가 랜덤 값으로 대신합니다 (백엔드 `moda.ai.mode=mock` 필요).
스타일 이미지는 [src/components/StyleImage.tsx](src/components/StyleImage.tsx)가 백엔드 `GET /api/v1/style-images`에서 `imageKey`로 찾아 표시합니다 (큰 사진 + 아이템 작은 사진). 이미지가 없으면 텍스트 박스로 대신합니다.

## 실행

```powershell
# 1) 백엔드 (다른 터미널)
cd backend; .\gradlew bootRun

# 2) 앱
cd mobile; npm install; npx expo start   # w = 브라우저, QR = Expo Go
```

서버 주소는 Expo 개발 서버의 PC IP로 **자동 설정**됩니다. 폰과 PC는 같은 Wi-Fi여야 하고, 처음 백엔드를 켤 때 Windows 방화벽 팝업이 뜨면 **허용**을 누르세요.
백엔드를 다른 PC에서 돌리거나 `--tunnel`을 쓸 때만 직접 지정합니다: `$env:EXPO_PUBLIC_API_URL="http://192.168.0.10:8080"`

폰에서는 **Expo Go** 앱을 설치하고 터미널의 QR을 스캔합니다 (iPhone은 카메라 앱, Android는 Expo Go 안에서).
안 되면: 폰 브라우저에서 `http://<PC IP>:8080/api/v1/health`가 열리는지 확인 → 안 열리면 방화벽 문제 (관리자 PowerShell: `New-NetFirewallRule -DisplayName moda-8080 -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow`).

## 챗봇을 로컬 LLM(Ollama)으로

백엔드를 아래 환경변수로 실행하면 됩니다 (기본은 템플릿 답변 Mock).

```powershell
$env:MODA_CHAT_MODE="external"
$env:MODA_CHAT_BASE_URL="http://localhost:11434/v1"
$env:MODA_CHAT_MODEL="qwen2.5:7b"
$env:MODA_CHAT_API_KEY="ollama"   # Ollama는 키를 안 보지만 비어 있으면 경고 로그
.\gradlew bootRun
```
