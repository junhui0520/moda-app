"""
실시간 체형 분석 데모 (웹캠 또는 휴대폰 영상)

  python run_live_body.py --height 175                    # 웹캠
  python run_live_body.py --height 175 --video 전신.mp4    # 휴대폰으로 찍은 영상 (권장)
  python run_live_body.py --height 175 --person kim       # 결과를 live_body_measurements.csv 에 누적
  python run_live_body.py --height 175 --rounds 3         # 측정 회차 고정 (기본 auto)

흐름
  ① 자세 안내 : 전신이 다 보이는지, 정면인지, A자 자세인지 등을 프레임마다 검사하고 안내
  ② 카운트다운 : 자세가 맞으면 3초 카운트다운 (그동안 자세가 흐트러지면 ①로)
  ③ 자동 수집 : 0.3초 간격으로 좋은 프레임 5개의 측정값을 모음 (= 1회차)
  ④ 자리 다시 : "한 걸음 옆으로 나갔다가 다시 제자리에" 안내 → 짧은 카운트다운 → 2회차
  ⑤ 판정     : 2회차 결과가 같으면 끝, 다르면 3회차까지 → 회차별 중앙값의 중앙값으로 체형 판정

키
  R : 다시 측정     S : 결과 저장 (--person 을 주면 자동 저장되므로 누를 필요 없음)
  D : 마스크·측정선 표시 켜기/끄기     Q 또는 ESC : 종료

촬영 팁
  · 휴대폰을 세로로, 허리 높이에 세워 두고 2~3m 떨어져 서기
  · 몸에 붙는 옷, 팔은 몸에서 살짝 뗀 A자 자세, 다리는 살짝 벌리기
  · 단색 배경, 혼자 나오게
※ 영상은 저장하지 않습니다. 측정값과 판정 결과만 저장됩니다.
"""
import argparse
import csv
import json
import math
import os
import sys
import time

os.environ.setdefault("GLOG_minloglevel", "2")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import cv2
import mediapipe as mp
import numpy as np

import body_shape as B
import config
from run_live import Painter

# 실시간에서만 쓰는 추가 안내
LIVE_ONLY = {
    "far": "조금 더 가까이 와 주세요",
    "center": "화면 가운데에 서 주세요",
    "moving": "잠시 움직이지 말아 주세요",
}
EN = {
    "no_person": "Stand in full view", "multi": "One person only", "body_hidden": "Whole body in frame",
    "feet_hidden": "Step back to show feet", "head_cut": "Head cut - step back", "feet_cut": "Step back a little",
    "far": "Come closer", "center": "Move to center", "not_standing": "Stand up straight",
    "side": "Face the camera", "tilt": "Stand straight", "arms": "Arms slightly away (A-pose)",
    "no_mask": "Use a plain background", "width_fail": "Face the camera", "moving": "Hold still",
    "legs": "Spread legs slightly",
}
PRIORITY = ["no_person", "multi", "body_hidden", "feet_hidden", "head_cut", "feet_cut", "far", "center",
            "not_standing", "side", "tilt", "arms", "no_mask", "width_fail", "implausible", "moving", "legs"]
FEATURE_KEYS = ["shoulder_dev", "waist_dev", "hip_dev", "trunk_dev", "taper_dev", "arm_dev", "limb_dev",
                "waist_hip_ratio", "shoulder_width_cm", "waist_width_cm", "hip_width_cm",
                "crotch_height_cm", "arm_length_cm"]


def blocking(code):
    if code == "legs":
        return False          # 샅높이는 판정에 안 쓰므로 안내만
    if code == "arms":
        return config.LIVE_BODY_REQUIRE_A_POSE
    return True


def message(code, korean):
    if not korean:
        return EN[code]
    if code in LIVE_ONLY:
        return LIVE_ONLY[code]
    return B.issue_text(code, live=True)


def assess(res, h, w, height_cm, prev_hip):
    issues, f, geo = B.measure(res, h, w, height_cm)
    if "top_y" in geo:
        frac = (geo["foot_y"] - geo["top_y"]) / h
        geo["height_frac"] = frac
        if frac < config.LIVE_BODY_MIN_HEIGHT_FRAC:
            issues.append("far")
        live_margin = config.LIVE_BODY_EDGE_MARGIN * h
        if geo["top_y"] <= live_margin and "head_cut" not in issues:
            issues.append("head_cut")
        if geo["foot_y"] >= h - live_margin and "feet_cut" not in issues:
            issues.append("feet_cut")
    hip = None
    P = geo.get("P")
    if P:
        hip = np.array([(P[B.L_HIP][0] + P[B.R_HIP][0]) / 2, (P[B.L_HIP][1] + P[B.R_HIP][1]) / 2])
        sh_y = (P[B.L_SH][1] + P[B.R_SH][1]) / 2
        torso = max(1.0, hip[1] - sh_y)
        if abs(hip[0] / w - 0.5) > config.LIVE_BODY_MAX_CENTER_OFFSET:
            issues.append("center")
        if prev_hip is not None and np.linalg.norm(hip - prev_hip) / torso > config.LIVE_BODY_MAX_MOTION:
            issues.append("moving")
    # 실시간에서는 어깨 기울기 기준을 조금 느슨하게 (랜드마크 떨림으로 안내가 깜빡이지 않게)
    if "tilt" in issues and geo.get("tilt", 0) <= config.LIVE_BODY_MAX_TILT_DEG:
        issues.remove("tilt")
    issues = sorted(set(issues), key=PRIORITY.index)
    return issues, f, geo, hip


def median_features(collected):
    out = {}
    for k in FEATURE_KEYS:
        vals = [c[k] for c in collected if c.get(k) is not None]
        out[k] = float(np.median(vals)) if vals else None
    return out


# ---------------------------------------------------------------------------
# 화면
# ---------------------------------------------------------------------------
GREEN, YELLOW, WHITE, GRAY, DARK = (80, 200, 80), (0, 210, 255), (255, 255, 255), (150, 150, 150), (25, 25, 25)


def dashed_hline(img, y, color):
    w = img.shape[1]
    for x in range(0, w, 24):
        cv2.line(img, (x, y), (min(w, x + 12), y), color, 2)


def draw(frame, painter, state, issues, geo, n_collected, target, countdown_left, final, show_overlay,
         rounds=None, planned=2, saved_flag=False):
    h, w = frame.shape[:2]
    rounds = rounds or []
    blk = [c for c in issues if blocking(c)]
    if show_overlay and geo:
        frame = B.draw_measurement(frame, geo, label=False)
    if state != "done":  # 서야 할 위치 안내: 머리 위·발 밑 여백선, 가운데 선
        m = int(config.LIVE_BODY_EDGE_MARGIN * h)
        dashed_hline(frame, h - m - 3, WHITE)
        for y in range(0, h, 30):
            cv2.line(frame, (w // 2, y), (w // 2, y + 12), (200, 200, 200), 1)

    big = max(22, h // 22)
    # 위쪽 안내 띠: 반투명 (머리 위치가 가려지지 않게)
    ov = frame.copy()
    cv2.rectangle(ov, (0, 0), (w, int(big * 1.7)), DARK, -1)
    frame = cv2.addWeighted(ov, 0.55, frame, 0.45, 0)
    if state != "done":
        dashed_hline(frame, int(config.LIVE_BODY_EDGE_MARGIN * h) + 2, WHITE)
    texts = []
    small = max(16, big * 2 // 3)
    n_round = len(rounds) + 1
    round_tag = (f"{n_round}회차" + (f" / 총 {planned}회" if planned > 1 else "")) if painter.korean else f"round {n_round}"
    if state in ("setup", "countdown", "collect"):
        texts.append((round_tag, (16, h - int(small * 3.2)), small, WHITE))
    if state == "setup":
        if blk:
            texts.append((message(blk[0], painter.korean), (16, int(big * 0.35)), big, YELLOW))
        else:
            texts.append(("좋아요!" if painter.korean else "Good!", (16, int(big * 0.35)), big, GREEN))
    elif state == "countdown":
        texts.append(("그대로 서 계세요" if painter.korean else "Hold this pose", (16, int(big * 0.35)), big, GREEN))
        n = str(max(1, math.ceil(countdown_left)))
        size = h // 4
        texts.append((n, (w // 2 - size // 4, h // 2 - size // 2), size, WHITE))
    elif state == "collect":
        msg = (message(blk[0], painter.korean) if blk else ("측정 중... 움직이지 마세요" if painter.korean else "Measuring..."))
        texts.append((msg, (16, int(big * 0.35)), big, YELLOW if blk else GREEN))
        bar = int((w - 40) * n_collected / target)
        cv2.rectangle(frame, (20, h - 40), (w - 20, h - 16), GRAY, 2)
        cv2.rectangle(frame, (20, h - 40), (20 + bar, h - 16), GREEN, -1)
    elif state == "between":
        texts.append(("한 걸음 옆으로 나갔다가 다시 제자리에 서 주세요" if painter.korean else "Step aside, then come back",
                      (16, int(big * 0.35)), big, YELLOW))
        texts.append((f"{len(rounds)}회차 완료 → 다음 측정 준비" if painter.korean else f"round {len(rounds)} done",
                      (16, h - int(small * 3.2)), small, WHITE))
    else:  # done
        texts.append(((("측정 완료 · 저장됨 · R 다시  Q 종료" if saved_flag else "측정 완료 · R 다시  S 저장  Q 종료"))
                      if painter.korean else "Done  R:retry S:save Q:quit",
                      (16, int(big * 0.35)), int(big * 0.8), GREEN))
        lines = (([f"특징: {final['reason']}"] if final.get("reason") else [])
                 + ([("회차별: " + " · ".join(final["round_results"]))] if len(final.get("round_results", [])) > 1 else [])
                 + ["[Size Korea 20대 남성 평균과 비교]"] + final["compare_to_size_korea"])
        hint = [c for c in issues if c == "legs"]
        f = final["features"]
        cm = [f"어깨 {f['shoulder_width_cm']:.1f}cm · 허리 {f['waist_width_cm']:.1f}cm · 엉덩이 {f['hip_width_cm']:.1f}cm"
              if painter.korean else
              f"shoulder {f['shoulder_width_cm']:.1f} waist {f['waist_width_cm']:.1f} hip {f['hip_width_cm']:.1f} cm"]
        panel_h = int(big * 1.6 + small * 1.5 * (len(lines) + 2 + (1 if hint else 0)))
        top = int(big * 2.0)
        ov = frame.copy()
        cv2.rectangle(ov, (12, top), (w - 12, top + panel_h), (20, 20, 20), -1)
        frame = cv2.addWeighted(ov, 0.78, frame, 0.22, 0)
        head = final["body_shape"] if painter.korean else final["body_shape_key"]
        texts.append(((f"체형: {head}" if painter.korean else f"Body: {head}"), (28, top + 8), big, WHITE))
        y = top + int(big * 1.6)
        basis = (f"(정면 폭 기준 · {final.get('n_rounds', 1)}회 측정 · 회차별 중앙값의 중앙값)"
                 if final.get("n_rounds", 1) > 1 else f"(정면 폭 기준 · {final['n_frames']}프레임 중앙값)")
        for t in (lines if painter.korean else []) + cm + ([basis] if painter.korean else []):
            texts.append((t, (28, y), small, WHITE))
            y += int(small * 1.5)
        if hint and painter.korean:
            texts.append(("※ 다리 사이가 안 보여 다리 비율은 측정 못 함", (28, y), small, YELLOW))
    return painter.texts(frame, texts)


# ---------------------------------------------------------------------------
# 회차 묶기
# ---------------------------------------------------------------------------
def close_round(collected):
    med = median_features(collected)
    return {"features": med, "key": B.classify(med), "frames": collected}


def combine_rounds(rounds):
    """회차별 중앙값 → 그 값들의 중앙값으로 최종 판정 (한 회차가 통째로 이상해도 끌려가지 않게)."""
    med = {}
    for k in FEATURE_KEYS:
        vals = [r["features"][k] for r in rounds if r["features"].get(k) is not None]
        med[k] = float(np.median(vals)) if vals else None
    final = B.summarize_body(med, n_photos=len(rounds))   # 최종 = 확률 1위 (몇 회 측정했는지 반영)
    final["features"] = {k: (None if v is None else round(v, 3)) for k, v in med.items()}
    final["n_rounds"] = len(rounds)
    final["round_results"] = [config.BODY_LABELS[r["key"]] for r in rounds]
    final["n_frames"] = sum(len(r["frames"]) for r in rounds)
    return final


def need_more_rounds(rounds, plan):
    """plan: 'auto' → 2회 일치하면 끝, 다르면 3회. 숫자 → 그 횟수만큼."""
    if plan == "auto":
        return len(rounds) < 2 or (len(rounds) == 2 and rounds[0]["key"] != rounds[1]["key"])
    return len(rounds) < int(plan)


# ---------------------------------------------------------------------------
# 저장
# ---------------------------------------------------------------------------
def save_result(final, rounds, person, height, outdir):
    """결과 JSON 저장. person 이 있으면 live_body_measurements.csv 에 '회차 단위'로 한 줄씩 누적."""
    os.makedirs(outdir, exist_ok=True)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    path = os.path.join(outdir, f"live_body_{stamp}.json")
    rnd = lambda d: {k: (round(v, 4) if isinstance(v, float) else v) for k, v in d.items()}
    with open(path, "w", encoding="utf-8") as fp:
        json.dump({"person_id": person, "height_cm": height, "result": final,
                   "rounds": [{"key": r["key"], "features": rnd(r["features"]), "frames": [rnd(c) for c in r["frames"]]}
                              for r in rounds]}, fp, ensure_ascii=False, indent=2)
    csv_path = None
    if person:  # consistency.py / calibrate.py / fit_body_population.py 에 바로 쓸 수 있는 형식
        csv_path = "live_body_measurements.csv"
        new = not os.path.exists(csv_path)
        with open(csv_path, "a", newline="", encoding="utf-8-sig") as fp:
            wr = csv.writer(fp)
            if new:
                wr.writerow(["person_id", "file", *FEATURE_KEYS, "predicted", "usable", "height_cm",
                             "session", "round", "n_frames", "final", "label"])
            for i, r in enumerate(rounds, 1):
                c = r["features"]
                wr.writerow([person, f"live_body_{stamp}_r{i}",
                             *[("" if c.get(k) is None else round(c[k], 4)) for k in FEATURE_KEYS],
                             r["key"], True, height, stamp, i, len(r["frames"]), final["body_shape_key"], ""])
    return path, csv_path


# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------
def beep(on):
    if not on:
        return
    try:
        import winsound
        winsound.Beep(1000, 120)
    except Exception:
        print("\a", end="", flush=True)


def main():
    ap = argparse.ArgumentParser(description="실시간 체형 분석")
    ap.add_argument("--height", type=float, required=True, help="키(cm) — 필수")
    ap.add_argument("--camera", type=int, default=0)
    ap.add_argument("--video", help="웹캠 대신 영상 파일 (휴대폰 세로 영상 권장)")
    ap.add_argument("--rotate", type=int, choices=[0, 90, 180, 270], default=0,
                    help="영상이 옆으로 누워 보이면 회전 (시계 방향 각도)")
    ap.add_argument("--rounds", default=str(config.LIVE_BODY_ROUNDS),
                    help="측정 회차: auto(2회 일치하면 끝, 다르면 3회) 또는 1·2·3 등 숫자")
    ap.add_argument("--frames", type=int, default=config.LIVE_BODY_TARGET_FRAMES, help="회차당 모을 프레임 수")
    ap.add_argument("--interval", type=float, default=config.LIVE_BODY_MIN_INTERVAL_SEC)
    ap.add_argument("--countdown", type=float, default=config.LIVE_BODY_COUNTDOWN_SEC, help="첫 회차 카운트다운(초)")
    ap.add_argument("--person", help="사람 이름 — 지정하면 자동 저장 + live_body_measurements.csv 에 누적")
    ap.add_argument("--allow-arms-down", action="store_true", help="팔이 몸에 붙어 있어도 측정 (A자 자세 검사 끄기)")
    ap.add_argument("--beep", action="store_true", help="카운트다운·완료 때 소리")
    ap.add_argument("--font", help="한글 폰트 파일 경로")
    ap.add_argument("--no-window", action="store_true", help="화면 없이 실행 (영상 파일 테스트용)")
    ap.add_argument("--vis-dir", default="results")
    a = ap.parse_args()
    if a.allow_arms_down:
        config.LIVE_BODY_REQUIRE_A_POSE = False
    if a.rounds != "auto" and not a.rounds.isdigit():
        sys.exit("--rounds 는 auto 또는 숫자(1, 2, 3 ...)로 지정하세요.")

    cap = cv2.VideoCapture(a.video if a.video else a.camera)
    if not a.video:
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    if not cap.isOpened():
        sys.exit("카메라/영상을 열 수 없습니다. (다른 프로그램이 카메라 사용 중인지, Mac 은 카메라 권한 확인)")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    rot = {90: cv2.ROTATE_90_CLOCKWISE, 180: cv2.ROTATE_180, 270: cv2.ROTATE_90_COUNTERCLOCKWISE}.get(a.rotate)
    painter = Painter(a.font)
    landmarker = B.make_video_landmarker()
    win = "Body shape (live)"
    if not a.no_window:
        cv2.namedWindow(win, cv2.WINDOW_NORMAL)

    state, collected, rounds, final = "setup", [], [], None
    t_count, bad_since, last_keep, prev_hip = None, None, -1e9, None
    between_start, seen_away = None, False
    show_overlay, last_print, frame_i, t0 = True, None, 0, time.monotonic()
    last_issues = []

    def countdown_len():
        return a.countdown if not rounds else min(a.countdown, config.LIVE_BODY_NEXT_COUNTDOWN_SEC)

    def planned():
        if a.rounds != "auto":
            return int(a.rounds)
        return 3 if len(rounds) >= 2 and need_more_rounds(rounds, "auto") else 2

    saved = False   # 이번 측정 결과를 이미 저장했는지 (자동 저장 후 S 를 눌러도 두 번 저장되지 않게)

    def save_once():
        nonlocal saved
        if saved:
            print("이미 저장된 결과입니다. (같은 측정을 두 번 저장하지 않음 — 새로 재려면 R)")
            return
        p, c = save_result(final, rounds, a.person, a.height, a.vis_dir)
        saved = True
        print(f"저장: {p}" + (f", {c} 에 누적" if c else ""))

    def reset_all():
        nonlocal state, collected, rounds, final, t_count, bad_since, last_keep, between_start, seen_away, saved
        state, collected, rounds, final = "setup", [], [], None
        t_count, bad_since, last_keep, between_start, seen_away = None, None, -1e9, None, False
        saved = False

    def finish_round(now):
        nonlocal state, collected, final, between_start, seen_away
        rounds.append(close_round(collected))
        collected = []
        if a.no_window:
            print(f"[{now:6.2f}s] {len(rounds)}회차 결과: {config.BODY_LABELS[rounds[-1]['key']]}")
        beep(a.beep)
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
        if rot is not None:
            frame = cv2.rotate(frame, rot)
        if not a.video:
            frame = cv2.flip(frame, 1)
        # 분석 속도를 위해 긴 변을 줄임 (화면도 이 크기로 표시)
        s = config.LIVE_BODY_PROCESS_MAX_SIDE / max(frame.shape[:2])
        if s < 1:
            frame = cv2.resize(frame, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
        h, w = frame.shape[:2]
        now = frame_i / fps if a.video else time.monotonic() - t0

        res = landmarker.detect_for_video(
            mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)), int(now * 1000))
        issues, f, geo, prev_hip = assess(res, h, w, a.height, prev_hip)
        last_issues = issues
        blk = [c for c in issues if blocking(c)]
        good = not blk and f is not None

        if state == "setup":
            if good:
                state, t_count, bad_since = "countdown", now, None
                beep(a.beep)
        elif state == "countdown":
            if not good:
                bad_since = bad_since if bad_since is not None else now
                if now - bad_since > 0.5:
                    state = "setup"
            else:
                bad_since = None
                if now - t_count >= countdown_len():
                    state, bad_since, last_keep = "collect", None, -1e9
                    beep(a.beep)
        elif state == "collect":
            if good:
                bad_since = None
                if now - last_keep >= a.interval:
                    collected.append(f)
                    last_keep = now
            else:
                bad_since = bad_since if bad_since is not None else now
                if now - bad_since > 1.5:  # 자세가 오래 흐트러지면 이번 회차만 다시
                    state, collected, bad_since = "setup", [], None
            if len(collected) >= a.frames:
                finish_round(now)
        elif state == "between":
            # 다음 회차 전에 자리를 한 번 벗어났다가 다시 서게 함 → 회차마다 서는 위치·자세가 달라져 오차가 상쇄됨
            away = any(c in issues for c in ("no_person", "body_hidden", "feet_hidden", "side", "center"))
            if away:
                seen_away = True
            timed_out = now - between_start > config.LIVE_BODY_REPOSITION_TIMEOUT_SEC
            if (seen_away or timed_out) and good:
                if a.no_window:
                    print(f"[{now:6.2f}s] 다음 회차 준비" + ("" if seen_away else " (자리 이동 없이 시간 초과로 진행)"))
                state, t_count, bad_since = "countdown", now, None

        if a.no_window:
            key = (state, blk[0] if blk else None, len(rounds))
            if key != last_print and state != "done":
                msg = "자리 다시 잡기 대기" if state == "between" else (message(blk[0], True) if blk else "OK")
                print(f"[{now:6.2f}s] {len(rounds) + 1}회차 {state:9s} {msg}  ({len(collected)}/{a.frames})")
                last_print = key
            if state == "done":
                break
            continue

        left = (countdown_len() - (now - t_count)) if state == "countdown" else 0
        view = draw(frame.copy(), painter, state, issues, geo, len(collected), a.frames, left, final, show_overlay,
                    rounds, planned(), saved)
        cv2.imshow(win, view)
        k = cv2.waitKey(1) & 0xFF
        if k in (ord("q"), 27):
            break
        if k == ord("d"):
            show_overlay = not show_overlay
        if k == ord("r"):
            reset_all()
        if k == ord("s") and final:
            save_once()
        if cv2.getWindowProperty(win, cv2.WND_PROP_VISIBLE) < 1:
            break

    if state != "done":
        if len(collected) >= 3:
            rounds.append(close_round(collected))
        if rounds:
            final = combine_rounds(rounds)
            print(f"\n영상이 끝나 {len(rounds)}회차 결과로 판정:", json.dumps(final, ensure_ascii=False, indent=2))
        else:
            tip = message(last_issues[0], True) if last_issues else ""
            print(f"\n측정에 쓸 프레임을 모으지 못했습니다 ({len(collected)}개). 마지막 안내: {tip}")
    cap.release()
    cv2.destroyAllWindows()
    landmarker.close()


if __name__ == "__main__":
    main()
