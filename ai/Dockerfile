# =====================================================================
# Moda AI 서버 (FastAPI + MediaPipe) — Railway · Render 등
#   빌드 컨텍스트: ai/  (플랫폼 Root Directory = ai)
#   로컬 빌드:  docker build -t moda-ai ./ai
#   로컬 실행:  docker run -p 8000:8000 moda-ai
#   포트: 플랫폼이 주입하는 PORT 환경변수를 사용 (없으면 8000)
#   백엔드 연결: 백엔드 환경변수 MODA_AI_MODE=external, MODA_AI_BASE_URL=<이 서버 주소>
# =====================================================================
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    GLOG_minloglevel=2 \
    MPLCONFIGDIR=/tmp/matplotlib \
    PORT=8000

# MediaPipe(libEGL·GLES 런타임) · OpenCV 가 쓰는 시스템 라이브러리
RUN apt-get update \
    && apt-get install -y --no-install-recommends libegl1 libgles2 libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 의존성 레이어 캐시: 패키지 목록이 바뀔 때만 다시 설치
COPY requirements-server.txt ./
RUN pip install --no-cache-dir -r requirements-server.txt

# MediaPipe 모델·측정 페이지용 웹 파일은 git 에 올리지 않으므로(.gitignore) 이미지 빌드 때 공식 저장소에서 받음
COPY download_models.py ./
RUN python download_models.py

COPY . .

# root 가 아닌 전용 사용자로 실행
RUN useradd --system --no-create-home moda && chown -R moda /app
USER moda

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:%s/health' % os.environ.get('PORT','8000'), timeout=4)" || exit 1

# MediaPipe 모델을 메모리에 한 벌만 올리도록 워커 1개 (요청은 서버 안에서 순서대로 처리, 1건 ≈ 0.3초)
CMD ["sh", "-c", "exec uvicorn server:app --host 0.0.0.0 --port ${PORT} --workers 1"]
