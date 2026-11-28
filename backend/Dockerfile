# =====================================================================
# Moda backend — 멀티스테이지 빌드 (Railway · Render 등)
#   빌드 컨텍스트: backend/  (플랫폼 Root Directory = backend)
#   로컬 빌드:  docker build -t moda-backend ./backend
#   로컬 실행:  docker run -p 8080:8080 moda-backend
#   포트: 플랫폼이 주입하는 PORT 환경변수를 application.yml의 server.port=${PORT:8080}이 자동 바인딩
# =====================================================================

# ---------- 1) Build: Gradle Wrapper로 실행 가능한 Boot JAR 생성 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사해 Gradle 배포판·의존성을 내려받아 둠
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew --no-daemon -q dependencies > /dev/null

# 소스 복사 후 JAR 빌드 (테스트는 CI·로컬 ./gradlew clean test에서 수행하므로 이미지 빌드에서는 생략)
COPY src ./src
RUN ./gradlew --no-daemon -q bootJar -x test \
    && cp build/libs/moda-backend-*.jar /workspace/app.jar

# ---------- 2) Runtime: Java 21 JRE 경량 이미지 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root가 아닌 전용 사용자로 실행
RUN addgroup -S moda && adduser -S moda -G moda
COPY --from=build --chown=moda:moda /workspace/app.jar /app/app.jar
USER moda

# 컨테이너 메모리 한도에 맞춘 힙, OOM 시 즉시 종료(플랫폼이 재시작), 응답 시간대는 앱이 KST로 고정하지만 로그도 KST로
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8" \
    PORT=8080

EXPOSE 8080

# Docker 자체 헬스체크 (Railway·Render는 대시보드의 Health Check Path 설정을 사용)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/api/v1/health" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
