#!/bin/sh
# Moda: 백엔드 + 앱 한 번에 실행 (macOS·Linux). Ctrl+C로 둘 다 종료
cd "$(dirname "$0")" || exit 1
command -v java >/dev/null || { echo "[moda] Java 21이 필요합니다: https://adoptium.net"; exit 1; }
command -v npm >/dev/null || { echo "[moda] Node.js가 필요합니다: https://nodejs.org"; exit 1; }

trap 'trap - INT TERM EXIT; kill 0' INT TERM EXIT
(cd backend && ./gradlew bootRun) &
cd mobile && npm install --no-audit --no-fund && EXPO_PUBLIC_USE_LOCAL_API=1 npx expo start
