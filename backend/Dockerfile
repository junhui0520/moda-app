# =====================================================================
# Moda backend — 멀티스테이지 빌드 (Railway · Render 등)
#   빌드 컨텍스트: **저장소 루트** (스타일 이미지 hair_images/ · style/ · sql/ 를 함께 담기 위해)
#   로컬 빌드:  docker build -f backend/Dockerfile -t moda-backend .      (저장소 루트에서)
#   로컬 실행:  docker run -p 8080:8080 moda-backend
#   Railway:    Root Directory = (비움, 저장소 루트) + 서비스 변수 RAILWAY_DOCKERFILE_PATH=backend/Dockerfile
#   포트: 플랫폼이 주입하는 PORT 환경변수를 application.yml의 server.port=${PORT:8080}이 자동 바인딩
#
# 컨테이너 안 배치는 로컬 저장소와 같은 모양이라 `cd backend && ./gradlew bootRun`과 같은 상대경로가 그대로 동작합니다.
#   /app/backend/app.jar   ← 실행 위치 (WORKDIR)
#   /app/hair_images/      ← WebConfig  file:../hair_images/  → /images/hair/**
#   /app/style/            ← WebConfig  file:../style/        → /images/style/**
#   /app/sql/              ← application.yml spring.sql.init  optional:file:../sql/*.sql (H2 기동 시 적재)
# 컨텍스트에서 제외할 폴더는 저장소 루트 .dockerignore 참고 (mobile/ · ai/ · docs/ · .git/ 등)
# =====================================================================

# ---------- 1) Build: backend/ 만으로 실행 가능한 Boot JAR 생성 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/backend

# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사해 Gradle 배포판·의존성을 내려받아 둠
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew --no-daemon -q dependencies > /dev/null

# 소스 복사 후 JAR 빌드 (테스트는 CI·로컬 ./gradlew clean test에서 수행하므로 이미지 빌드에서는 생략)
COPY backend/src ./src
RUN ./gradlew --no-daemon -q bootJar -x test \
    && cp build/libs/moda-backend-*.jar /workspace/app.jar

# ---------- 2) Runtime: Java 21 JRE 경량 이미지 ----------
FROM eclipse-temurin:21-jre-alpine

# root가 아닌 전용 사용자로 실행
RUN addgroup -S moda && adduser -S moda -G moda

# 스타일 이미지·SQL (자주 바뀌지 않으므로 JAR보다 먼저 복사해 레이어 캐시 활용).
# --chown: 빌드 호스트의 파일 권한과 관계없이 실행 사용자(moda)가 항상 읽을 수 있도록 소유자를 지정
COPY --chown=moda:moda hair_images /app/hair_images
COPY --chown=moda:moda style /app/style
COPY --chown=moda:moda sql /app/sql
COPY --from=build --chown=moda:moda /workspace/app.jar /app/backend/app.jar

WORKDIR /app/backend
USER moda

# 빌드 단계에서 실행 사용자가 읽을 수 있는지 검증 (폴더가 빠졌거나 권한이 없으면 이미지 빌드 실패)
RUN test -r ../sql/schema_mysql.sql \
    && test -r ../sql/load_outfits.sql \
    && test -r ../hair_images/REGENT_CUT/01.png \
    && test -r ../style/CASUAL/CASUAL_01/full_shot.png \
    && test -r app.jar

# 컨테이너 메모리 한도에 맞춘 힙, OOM 시 즉시 종료(플랫폼이 재시작), 응답 시간대는 앱이 KST로 고정하지만 로그도 KST로
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8" \
    PORT=8080

EXPOSE 8080

# Docker 자체 헬스체크 (Railway·Render는 대시보드의 Health Check Path 설정을 사용)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/api/v1/health" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
