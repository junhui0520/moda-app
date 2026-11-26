"""
실시간 얼굴형 분석 데모 (웹캠)

  python run_live.py                     # 기본 웹캠
  python run_live.py --camera 1          # 다른 카메라
  python run_live.py --person kim        # 결과를 live_measurements.csv 에 누적 (비교 실험용)
  python run_live.py --video 영상.mp4     # 웹캠 대신 영상 파일 (휴대폰으로 찍은 영상 등)
  python run_live.py --rounds 3          # 측정 회차 고정 (기본 auto: 2회 일치하면 끝, 다르면 3회)

흐름
  카메라 프레임마다 → 얼굴 랜드마크·고개 각도·표정 → 품질 검사
  → 실패하면 화면에 안내 문구 ("정면을 봐 주세요" 등)
  → 통과한 프레임 중 일정 간격(기본 0.35초)마다 하나씩 측정값을 모음
  → 회차당 목표 개수(기본 6개)가 모이면 그 회차의 중앙값 계산
  → "고개를 살짝 돌렸다가 다시 정면" 안내 후 다음 회차 (회차마다 조건이 조금씩 달라야 오차가 상쇄됨)
  → 2회차 결과가 같으면 끝, 다르면 3회차까지 → 회차별 중앙값의 중앙값으로 최종 판정

키
  R : 다시 측정     S : 결과 저장 (--person 을 주면 자동 저장되므로 누를 필요 없음)
  D : 측정값 표시 켜기/끄기     Q 또는 ESC : 종료

※ 영상은 저장하지 않습니다. S 를 누르면 판정 결과(JSON)만 저장됩니다.
"""
import argparse
import csv
import json
import os
import sys
import time

os.environ.setdefault("GLOG_minloglevel", "2")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision
from PIL import Image, ImageDraw, ImageFont

import config
from common import model_path
from face_shape import FACE_OVAL, FEATURE_KEYS, expression_scores, features_from_landmarks, head_pose, summarize

NOSE_TIP = 1

# ---------------------------------------------------------------------------
# 한글 글자 그리기 (OpenCV 는 한글을 못 그려서 PIL 사용)
# ---------------------------------------------------------------------------
FONT_CANDIDATES = [
    "C:/Windows/Fonts/malgun.ttf",                              # Windows 맑은 고딕
    "/System/Library/Fonts/AppleSDGothicNeo.ttc",               # macOS
    "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
    "/Library/Fonts/AppleGothic.ttf",
    "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",          # Linux
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
]


class Painter:
    def __init__(self, font_path=None):
        self.path = font_path or next((p for p in FONT_CANDIDATES if os.path.exists(p)), None)
        self.cache = {}

    @property
    def korean(self):
        return self.path is not None

    def font(self, size):
        if size not in self.cache:
            self.cache[size] = ImageFont.truetype(self.path, size)
        return self.cache[size]

    def texts(self, bgr, items):
        """items: [(text, (x, y), size, (B,G,R)), ...] 를 한 번에 그림."""
        if not items:
            return bgr
        if not self.korean:  # 한글 폰트가 없으면 영어 대체 문구로
            W = bgr.shape[1]
            for t, (x, y), size, col in items:
                scale = size / 32
                while scale > 0.35 and x + cv2.getTextSize(t, cv2.FONT_HERSHEY_SIMPLEX, scale, 2)[0][0] > W - 12:
                    scale -= 0.05
                cv2.putText(bgr, t, (x, y + size), cv2.FONT_HERSHEY_SIMPLEX, scale, col, 2)
            return bgr
        img = Image.fromarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
        d = ImageDraw.Draw(img)
        W = bgr.shape[1]
        for t, (x, y), size, col in items:
            # 화면 폭을 넘으면 글자 크기를 줄여서 맞춤 (세로 영상처럼 폭이 좁을 때)
            while size > 12 and x + self.font(size).getlength(t) > W - 12:
                size -= 2
            d.text((x, y), t, font=self.font(size), fill=(col[2], col[1], col[0]))
        return cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)


# ---------------------------------------------------------------------------
# 프레임 품질 검사
# ---------------------------------------------------------------------------
# (코드, 한글 안내, 영어 대체 안내) — 위에 있을수록 먼저 안내
MSG = {
    "no_face": ("얼굴이 보이지 않아요", "No face"),
    "multi": ("한 명만 화면에 나와 주세요", "One person only"),
    "far": ("조금 더 가까이 와 주세요", "Come closer"),
    "close": ("조금 뒤로 가 주세요 (가까우면 얼굴이 왜곡돼요)", "Move back a little"),
    "center": ("얼굴을 화면 가운데에 맞춰 주세요", "Center your face"),
    "dark": ("더 밝은 곳에서 해 주세요", "Too dark"),
    "yaw": ("정면을 봐 주세요", "Look straight"),
    "pitch_up": ("턱을 조금 내려 주세요", "Lower your chin"),
    "pitch_down": ("턱을 조금 들어 주세요", "Raise your chin"),
    "jaw": ("입을 다물어 주세요", "Close your mouth"),
    "smile": ("무표정으로 있어 주세요", "Neutral face"),
    "moving": ("잠시 움직이지 말아 주세요", "Hold still"),
}


def assess(res, bgr, prev_nose):
    """한 프레임 → (문제 코드 목록, 특징 dict 또는 None, 화면용 정보)."""
    h, w = bgr.shape[:2]
    info = {}
    if not res.face_landmarks:
        return ["no_face"], None, info, None
    issues = []
    if len(res.face_landmarks) > 1:
        issues.append("multi")
    lm = res.face_landmarks[0]
    xs = np.array([q.x for q in lm]) * w
    ys = np.array([q.y for q in lm]) * h
    info["oval"] = np.array([(xs[i], ys[i]) for i in FACE_OVAL], np.int32)
    face_w = xs.max() - xs.min()
    frac = face_w / w
    if frac < config.LIVE_MIN_FACE_FRAC:
        issues.append("far")
    elif frac > config.LIVE_MAX_FACE_FRAC:
        issues.append("close")
    cx, cy = xs.mean() / w, ys.mean() / h
    if abs(cx - 0.5) > config.LIVE_MAX_CENTER_OFFSET or abs(cy - 0.5) > config.LIVE_MAX_CENTER_OFFSET + 0.05:
        issues.append("center")
    x0, x1 = int(max(0, xs.min())), int(min(w, xs.max()))
    y0, y1 = int(max(0, ys.min())), int(min(h, ys.max()))
    if x1 > x0 and y1 > y0:
        bright = float(cv2.cvtColor(bgr[y0:y1, x0:x1], cv2.COLOR_BGR2GRAY).mean())
        info["brightness"] = bright
        if bright < config.LIVE_MIN_BRIGHTNESS:
            issues.append("dark")

    pose = head_pose(res)
    if pose:
        yaw, pitch, _ = pose
        info["yaw"], info["pitch"] = yaw, pitch
        if abs(yaw) > config.FACE_MAX_YAW_DEG:
            issues.append("yaw")
        if pitch > config.FACE_MAX_PITCH_DEG:
            issues.append("pitch_up")
        elif pitch < -config.FACE_MAX_PITCH_DEG:
            issues.append("pitch_down")
    ex = expression_scores(res)
    if ex:
        info.update(ex)
        if ex["jaw_open"] > config.FACE_MAX_JAW_OPEN:
            issues.append("jaw")
        if ex["smile"] > config.FACE_MAX_SMILE:
            issues.append("smile")

    nose = np.array([xs[NOSE_TIP], ys[NOSE_TIP]])
    if prev_nose is not None and np.linalg.norm(nose - prev_nose) / face_w > config.LIVE_MAX_MOTION:
        issues.append("moving")

    f, _ = features_from_landmarks(lm, w, h)
    order = list(MSG)
    issues.sort(key=order.index)
    return issues, f, info, nose


# ---------------------------------------------------------------------------
# 화면 그리기
# ---------------------------------------------------------------------------
GREEN, RED, YELLOW, WHITE, GRAY = (80, 200, 80), (60, 60, 230), (0, 210, 255), (255, 255, 255), (150, 150, 150)


def draw(frame, painter, state, issues, info, collected, target, final, show_debug, last_f,
         rounds=None, max_rounds=3, round_plan="auto", saved_flag=False):
    h, w = frame.shape[:2]
    rounds = rounds or []
    ok = not issues
    if state in ("collect", "between"):  # 얼굴을 맞출 위치 안내 (가운데 점선 타원)
        ax_w = int(w * (config.LIVE_MIN_FACE_FRAC + config.LIVE_MAX_FACE_FRAC) / 4)
        for a0 in range(0, 360, 12):
            cv2.ellipse(frame, (w // 2, h // 2), (ax_w, int(ax_w * 1.35)), 0, a0, a0 + 6, (40, 40, 40), 4)
            cv2.ellipse(frame, (w // 2, h // 2), (ax_w, int(ax_w * 1.35)), 0, a0, a0 + 6, (255, 255, 255), 2)
    if "oval" in info:
        col = GRAY if state == "between" else GREEN if (ok or state == "done") else RED
        cv2.polylines(frame, [info["oval"]], True, col, 2)
    # 위쪽 안내 띠
    cv2.rectangle(frame, (0, 0), (w, 56), (30, 30, 30), -1)
    texts = []
    n_round = len(rounds) + (1 if state == "collect" else 0)
    planned = 2 if round_plan == "auto" and len(rounds) < 2 else max_rounds if round_plan != "auto" else 3
    if state == "collect":
        msg = ("좋아요, 그대로 있어 주세요", "Good, hold still") if ok else MSG[issues[0]]
        texts.append((msg[0] if painter.korean else msg[1], (16, 12), 28, GREEN if ok else YELLOW))
        bar_w = int((w - 40) * len(collected) / target)
        cv2.rectangle(frame, (20, h - 34), (w - 20, h - 16), GRAY, 2)
        cv2.rectangle(frame, (20, h - 34), (20 + bar_w, h - 16), GREEN, -1)
        label = (f"{n_round}회차 측정 {len(collected)}/{target}" + ("" if planned <= 1 else f"   (총 {planned}회)")
                 if painter.korean else f"round {n_round}  {len(collected)}/{target}")
        texts.append((label, (24, h - 66), 22, WHITE))
    elif state == "between":
        texts.append(("고개를 살짝 옆으로 돌렸다가 다시 정면을 봐 주세요" if painter.korean else "Turn away, then look back",
                      (16, 12), 26, YELLOW))
        texts.append((f"{len(rounds)}회차 완료 → 다음 측정 준비" if painter.korean else f"round {len(rounds)} done",
                      (24, h - 66), 22, WHITE))
    else:
        texts.append((("측정 완료 · 저장됨  ·  R 다시 측정   Q 종료" if saved_flag else "측정 완료  ·  R 다시 측정   S 저장   Q 종료")
                      if painter.korean else "Done  R:retry S:save Q:quit",
                      (16, 12), 26, GREEN))
        extra = sum(34 for x in (final.get("reason") and painter.korean, final.get("borderline"),
                                 len(rounds) > 1 and painter.korean) if x)
        panel_h = 60 + 44 * len(final["candidates"]) + extra
        overlay = frame.copy()
        cv2.rectangle(overlay, (20, 76), (min(w - 20, 560), 76 + panel_h), (20, 20, 20), -1)
        frame[:] = cv2.addWeighted(overlay, 0.75, frame, 0.25, 0)
        head = f"얼굴형: {final['face_shape']}" if painter.korean else f"Face: {final['face_shape_key']}"
        texts.append((head, (36, 88), 32, WHITE))
        for i, c in enumerate(final["candidates"]):
            y = 136 + 44 * i
            bw = int(300 * c["prob"])
            cv2.rectangle(frame, (170, y + 6), (170 + bw, y + 30), GREEN if i == 0 else GRAY, -1)
            name = c["face_shape"] if painter.korean else c["key"]
            texts.append((name, (36, y + 2), 24, WHITE))
            texts.append((f"{c['prob']:.0%}", (180 + bw, y + 2), 22, WHITE))
        y_extra = 76 + 60 + 44 * len(final["candidates"])
        if len(rounds) > 1 and painter.korean:
            texts.append(("회차별: " + " · ".join(config.FACE_LABELS[r["key"]] for r in rounds), (36, y_extra), 20,
                          (210, 210, 210)))
            y_extra += 34
        if final.get("reason") and painter.korean:
            texts.append(("특징: " + final["reason"], (36, y_extra), 20, WHITE))
            y_extra += 34
        if final.get("borderline"):
            texts.append(("두 유형의 경계에 있는 얼굴이에요" if painter.korean else "Borderline face",
                          (36, y_extra), 20, YELLOW))
    if show_debug and last_f:
        y = 70 if state != "done" else 100 + 60 + 44 * 3 + 60
        dbg = [f"{k}: {last_f[k]:.3f}" for k in FEATURE_KEYS]
        dbg += [f"yaw {info.get('yaw', 0):+.0f}  pitch {info.get('pitch', 0):+.0f}",
                f"jaw_open {info.get('jaw_open', 0):.2f}  smile {info.get('smile', 0):.2f}",
                f"bright {info.get('brightness', 0):.0f}"]
        for i, t in enumerate(dbg):
            cv2.putText(frame, t, (w - 330, y + 22 * i), cv2.FONT_HERSHEY_SIMPLEX, 0.55, YELLOW, 1)
    return painter.texts(frame, texts)


# ---------------------------------------------------------------------------
# 저장
# ---------------------------------------------------------------------------
def save_result(final, rounds, person, outdir):
    """결과 JSON 저장. person 이 있으면 live_measurements.csv 에 '회차 단위'로 한 줄씩 누적.
    (한 회차 안의 프레임들은 몇 초 안에 찍혀 서로 비슷하므로, 반복 측정의 단위는 회차)"""
    os.makedirs(outdir, exist_ok=True)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    path = os.path.join(outdir, f"live_{stamp}.json")
    with open(path, "w", encoding="utf-8") as fp:
        json.dump({"person_id": person, "result": final,
                   "rounds": [{"key": r["key"], "features": {k: round(v, 4) for k, v in r["features"].items()},
                               "frames": [{k: round(v, 4) for k, v in f.items()} for f in r["frames"]]}
                              for r in rounds]},
                  fp, ensure_ascii=False, indent=2)
    if not person:
        return path, None
    csv_path = "live_measurements.csv"
    new = not os.path.exists(csv_path)
    with open(csv_path, "a", newline="", encoding="utf-8-sig") as fp:
        wr = csv.writer(fp)
        if new:
            wr.writerow(["person_id", "file", *FEATURE_KEYS, "predicted", "usable", "session", "round",
                         "n_frames", "final"])
        for i, r in enumerate(rounds, 1):
            wr.writerow([person, f"live_{stamp}_r{i}", *[round(r["features"][k], 4) for k in FEATURE_KEYS],
                         r["key"], True, stamp, i, len(r["frames"]), final["face_shape_key"]])
    return path, csv_path


# ---------------------------------------------------------------------------
# 메인 루프
# ---------------------------------------------------------------------------
def make_landmarker():
    opts = vision.FaceLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path(config.FACE_MODEL)),
        running_mode=vision.RunningMode.VIDEO,
        num_faces=2,
        output_face_blendshapes=True,
        output_facial_transformation_matrixes=True,
    )
    return vision.FaceLandmarker.create_from_options(opts)


def combine_rounds(rounds):
    """회차별 중앙값 → 그 값들의 중앙값으로 최종 판정 (한 회차가 통째로 이상해도 끌려가지 않게).
    최종 유형 = 화면에 보이는 확률 1위 (summarize 가 정함). 그래서 유형과 퍼센트가 항상 일치하고,
    1위 확률이 FACE_MIN_CONFIDENCE(60%) 미만이면 '경계 얼굴' 안내가 같이 붙습니다."""
    med = {k: float(np.median([r["features"][k] for r in rounds])) for k in FEATURE_KEYS}
    out = summarize(med, n_photos=len(rounds))
    out["features"] = {k: round(v, 3) for k, v in med.items()}
    out["n_rounds"] = len(rounds)
    out["round_results"] = [config.FACE_LABELS[r["key"]] for r in rounds]
    out["n_frames"] = sum(len(r["frames"]) for r in rounds)
    return out


def need_more_rounds(rounds, plan):
    """plan: 'auto' → 2회 일치하면 끝, 다르면 3회. 숫자 → 그 횟수만큼."""
    if plan == "auto":
        if len(rounds) < 2:
            return True
        if len(rounds) == 2:
            return rounds[0]["key"] != rounds[1]["key"]
        return False
    return len(rounds) < int(plan)


def main():
    ap = argparse.ArgumentParser(description="실시간 얼굴형 분석 (웹캠)")
    ap.add_argument("--camera", type=int, default=0, help="카메라 번호 (기본 0)")
    ap.add_argument("--video", help="웹캠 대신 영상 파일")
    ap.add_argument("--rounds", default=str(config.LIVE_ROUNDS),
                    help="측정 회차: auto(2회 일치하면 끝, 다르면 3회) 또는 1·2·3 등 숫자")
    ap.add_argument("--frames", type=int, default=config.LIVE_TARGET_FRAMES, help="회차당 모을 프레임 수")
    ap.add_argument("--interval", type=float, default=config.LIVE_MIN_INTERVAL_SEC, help="프레임 사이 최소 간격(초)")
    ap.add_argument("--person", help="사람 이름 — 지정하면 결과를 자동 저장하고 live_measurements.csv 에 누적")
    ap.add_argument("--font", help="한글 폰트 파일 경로 (자동으로 못 찾을 때)")
    ap.add_argument("--no-window", action="store_true", help="화면 없이 실행 (영상 파일 테스트용)")
    ap.add_argument("--vis-dir", default="results")
    a = ap.parse_args()
    if a.rounds != "auto" and not a.rounds.isdigit():
        sys.exit("--rounds 는 auto 또는 숫자(1, 2, 3 ...)로 지정하세요.")
    max_rounds = 3 if a.rounds == "auto" else int(a.rounds)

    src = a.video if a.video else a.camera
    cap = cv2.VideoCapture(src)
    if not a.video:
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    if not cap.isOpened():
        sys.exit(f"카메라/영상을 열 수 없습니다: {src}\n"
                 "· 다른 프로그램(줌, 카톡 등)이 카메라를 쓰고 있지 않은지\n"
                 "· Mac: 시스템 설정 → 개인정보 보호 → 카메라 에서 터미널 허용\n"
                 "· 카메라가 여러 개면 --camera 1 로 바꿔 보세요")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    painter = Painter(a.font)
    if not painter.korean and not a.no_window:
        print("한글 폰트를 찾지 못해 영어 안내로 표시합니다. (--font 로 지정 가능)")

    from face_shape import classify
    landmarker = make_landmarker()
    win = "Face shape (live)"
    state, collected, rounds, final = "collect", [], [], None
    last_keep, prev_nose, last_f, show_debug = -1e9, None, None, False
    last_msg, frame_i, t0 = None, 0, time.monotonic()
    between_start, seen_away = None, False

    saved = False   # 이번 측정 결과를 이미 저장했는지 (자동 저장 후 S 를 눌러도 두 번 저장되지 않게)

    def save_once():
        nonlocal saved
        if saved:
            print("이미 저장된 결과입니다. (같은 측정을 두 번 저장하지 않음 — 새로 재려면 R)")
            return
        p, c = save_result(final, rounds, a.person, a.vis_dir)
        saved = True
        print(f"저장: {p}" + (f", {c} 에 누적" if c else ""))

    def reset():
        nonlocal state, collected, rounds, final, last_keep, between_start, seen_away, saved
        state, collected, rounds, final, last_keep = "collect", [], [], None, -1e9
        between_start, seen_away, saved = None, False, False

    def close_round(now):
        nonlocal state, collected, final, between_start, seen_away
        med = {k: float(np.median([f[k] for f in collected])) for k in FEATURE_KEYS}
        rounds.append({"features": med, "key": classify(med), "frames": collected})
        if a.no_window:
            print(f"[{now:6.2f}s] {len(rounds)}회차 결과: {config.FACE_LABELS[rounds[-1]['key']]}")
        collected = []
        if need_more_rounds(rounds, a.rounds):
            state, between_start, seen_away = "between", now, False
            return
        final = combine_rounds(rounds)
        state = "done"
        print("\n결과:", json.dumps(final, ensure_ascii=False, indent=2))
        if a.person:
            save_once()

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_i += 1
        if not a.video:
            frame = cv2.flip(frame, 1)  # 거울처럼 보이게 (얼굴형 측정값에는 영향 없음)
        # 타임스탬프: 영상 파일은 프레임 번호 기준, 웹캠은 실제 시간 기준 (항상 증가해야 함)
        now = frame_i / fps if a.video else time.monotonic() - t0
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        res = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), int(now * 1000))
        issues, f, info, prev_nose = assess(res, frame, prev_nose)
        if f:
            last_f = f

        if state == "collect":
            if not issues and f and now - last_keep >= a.interval:
                collected.append(f)
                last_keep = now
            if len(collected) >= a.frames:
                close_round(now)
        elif state == "between":
            # 다음 회차 전에 자세를 한 번 흐트러뜨렸다가 다시 잡게 함 → 회차마다 조건이 달라져 오차가 상쇄됨
            away = (not res.face_landmarks) or abs(info.get("yaw", 0)) > config.LIVE_REPOSITION_YAW_DEG \
                or abs(info.get("pitch", 0)) > config.LIVE_REPOSITION_YAW_DEG
            if away:
                seen_away = True
            timed_out = now - between_start > config.LIVE_REPOSITION_TIMEOUT_SEC
            if (seen_away or timed_out) and not issues and f:
                if a.no_window:
                    print(f"[{now:6.2f}s] 다음 회차 시작" + ("" if seen_away else " (자세 재정렬 없이 시간 초과로 진행)"))
                state, last_keep = "collect", now  # 바로 첫 프레임을 받지 않고 interval 뒤부터

        if a.no_window and state in ("collect", "between"):
            msg = ("OK" if not issues else MSG[issues[0]][0]) if state == "collect" else "자세 다시 잡기 대기"
            if msg != last_msg:
                print(f"[{now:6.2f}s] {msg}  ({len(rounds) + 1}회차 {len(collected)}/{a.frames})")
                last_msg = msg

        if a.no_window:
            if state == "done":
                break
            continue

        view = draw(frame.copy(), painter, state, issues, info, collected, a.frames, final, show_debug, last_f,
                    rounds, max_rounds, a.rounds, saved)
        cv2.imshow(win, view)
        key = cv2.waitKey(1) & 0xFF
        if key in (ord("q"), 27):
            break
        if key == ord("d"):
            show_debug = not show_debug
        if key == ord("r"):
            reset()
        if key == ord("s") and final:
            save_once()
        if cv2.getWindowProperty(win, cv2.WND_PROP_VISIBLE) < 1:
            break

    if state != "done":
        if len(collected) >= 3:
            med = {k: float(np.median([f[k] for f in collected])) for k in FEATURE_KEYS}
            rounds.append({"features": med, "key": classify(med), "frames": collected})
        if rounds:
            final = combine_rounds(rounds)
            print(f"\n영상이 끝나 {len(rounds)}회차 결과로 판정:", json.dumps(final, ensure_ascii=False, indent=2))
        else:
            print(f"\n쓸 만한 프레임이 {len(collected)}개뿐이라 판정하지 못했습니다. 안내 문구를 참고해 다시 시도하세요.")
    cap.release()
    cv2.destroyAllWindows()
    landmarker.close()


if __name__ == "__main__":
    main()
