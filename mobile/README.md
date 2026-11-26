# Moda Mobile (Expo)

결과 화면 + 챗봇 화면. 기본 정보 입력·얼굴·체형 분석은 아직 없고 [src/randomDemo.ts](src/randomDemo.ts)가 랜덤 값으로 대신합니다 (백엔드 `moda.ai.mode=mock` 필요).
스타일 이미지는 [src/components/StyleImage.tsx](src/components/StyleImage.tsx)가 텍스트 박스로 대신합니다. DB 이미지가 준비되면 `imageKey`로 이미지를 찾아 이 컴포넌트만 바꾸면 됩니다.

## 실행

```powershell
# 1) 백엔드 (다른 터미널)
cd backend; .\gradlew bootRun

# 2) 앱
cd mobile; npm install; npx expo start   # w = 브라우저, QR = Expo Go
```

PC 브라우저·iOS 시뮬레이터 외에는 `localhost`가 PC가 아니므로 서버 주소를 지정합니다.

```powershell
$env:EXPO_PUBLIC_API_URL="http://10.0.2.2:8080"        # Android 에뮬레이터
$env:EXPO_PUBLIC_API_URL="http://192.168.0.10:8080"    # 실기기 (PC의 LAN IP, 같은 Wi-Fi)
npx expo start
```

## 챗봇을 로컬 LLM(Ollama)으로

백엔드를 아래 환경변수로 실행하면 됩니다 (기본은 템플릿 답변 Mock).

```powershell
$env:MODA_CHAT_MODE="external"
$env:MODA_CHAT_BASE_URL="http://localhost:11434/v1"
$env:MODA_CHAT_MODEL="qwen2.5:7b"
$env:MODA_CHAT_API_KEY="ollama"   # Ollama는 키를 안 보지만 비어 있으면 경고 로그
.\gradlew bootRun
```
