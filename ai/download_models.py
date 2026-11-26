"""Google 공식 저장소에서 MediaPipe 모델 파일을 내려받습니다. (최초 1회)

  python download_models.py           # 얼굴 + 전신(full) 모델 + 휴대폰 실시간 페이지용 MediaPipe 웹 파일
  python download_models.py --heavy   # 전신 heavy 모델도 함께 (더 정확, 더 느림)
  python download_models.py --no-web  # 웹 파일은 받지 않음 (그러면 페이지가 CDN 에서 받음)
"""
import io
import os
import sys
import tarfile
import urllib.request

# 휴대폰 실시간 페이지(live/)가 쓰는 MediaPipe 웹 버전 — Python mediapipe 와 같은 버전으로 맞춤
TASKS_VISION_VERSION = "1.2.3"
TASKS_VISION_URL = f"https://registry.npmjs.org/@mediapipe/tasks-vision/-/tasks-vision-{TASKS_VISION_VERSION}.tgz"
WEB_FILES = ["vision_bundle.mjs", "wasm/vision_wasm_internal.js", "wasm/vision_wasm_internal.wasm"]

BASE = "https://storage.googleapis.com/mediapipe-models"
MODELS = {
    "face_landmarker.task": f"{BASE}/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
    "pose_landmarker_full.task": f"{BASE}/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task",
}
HEAVY = {
    "pose_landmarker_heavy.task": f"{BASE}/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task",
}


def main():
    target = dict(MODELS)
    if "--heavy" in sys.argv:
        target.update(HEAVY)
    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
    os.makedirs(out_dir, exist_ok=True)
    for name, url in target.items():
        dst = os.path.join(out_dir, name)
        if os.path.exists(dst) and os.path.getsize(dst) > 1_000_000:
            print(f"이미 있음: {name}")
            continue
        print(f"다운로드: {name} ...", end=" ", flush=True)
        urllib.request.urlretrieve(url, dst)
        print(f"{os.path.getsize(dst) / 1e6:.1f} MB")
    print("완료 →", out_dir)
    if "--no-web" not in sys.argv:
        download_web()


def download_web():
    """npm 저장소에서 @mediapipe/tasks-vision 을 받아 live/vendor/ 에 필요한 파일만 풀어 둠."""
    root = os.path.dirname(os.path.abspath(__file__))
    vendor = os.path.join(root, "live", "vendor")
    stamp = os.path.join(vendor, "VERSION")
    if os.path.exists(stamp) and open(stamp).read().strip() == TASKS_VISION_VERSION and \
            all(os.path.exists(os.path.join(vendor, f)) for f in WEB_FILES):
        print(f"이미 있음: MediaPipe 웹 파일 {TASKS_VISION_VERSION}")
        return
    print(f"다운로드: MediaPipe 웹 파일 {TASKS_VISION_VERSION} ...", end=" ", flush=True)
    data = urllib.request.urlopen(TASKS_VISION_URL).read()
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as tar:
        for name in WEB_FILES:
            member = tar.getmember("package/" + name)
            dst = os.path.join(vendor, *name.split("/"))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with tar.extractfile(member) as src, open(dst, "wb") as out:
                out.write(src.read())
    with open(stamp, "w") as fp:
        fp.write(TASKS_VISION_VERSION)
    print(f"{len(data) / 1e6:.1f} MB → {vendor}")


if __name__ == "__main__":
    main()
