@echo off
rem Moda: double-click to run backend + app (Windows)
cd /d "%~dp0"

where java >nul 2>&1 || (echo [moda] Java 21 is required: https://adoptium.net & pause & exit /b 1)
where npm >nul 2>&1 || (echo [moda] Node.js is required: https://nodejs.org & pause & exit /b 1)

rem First run only: allow phones on the same Wi-Fi to reach port 8080 (asks for admin once)
netsh advfirewall firewall show rule name=moda-8080 >nul 2>&1 || powershell -NoProfile -Command "Start-Process netsh -Verb RunAs -Wait -ArgumentList 'advfirewall firewall add rule name=moda-8080 dir=in action=allow protocol=TCP localport=8080'"

start "moda-backend" /d "%~dp0backend" cmd /k gradlew.bat bootRun

cd mobile
call npm install --no-audit --no-fund || (pause & exit /b 1)
echo.
echo [moda] Backend is starting in the other window (about 30s on first run).
echo [moda] Press w for browser, or scan the QR with Expo Go on a phone on the same Wi-Fi.
echo.
set EXPO_PUBLIC_USE_LOCAL_API=1
call npx expo start
