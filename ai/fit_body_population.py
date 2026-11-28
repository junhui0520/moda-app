"""
평균 체형 기준 맞추기 — '작은 역삼각체형·표준체형이 거의 안 나온다' 문제 해결용

사진으로 잰 몸 폭은 옷 두께·자세·머리카락/신발(키 환산) 때문에 Size Korea 실측 평균과 체계적으로 다릅니다.
예) 옷 때문에 몸통이 평균보다 6% 굵게 재지면 → '몸통이 가는' 작은 역삼각체형은 거의 안 나옴.
그래서 이 스크립트는 팀이 잰 사람들의 평균을 기준점으로 삼고(공통 치우침 상쇄),
유형 비율이 Size Korea 20대 남성 구성비와 비슷해지도록 판정 기준을 계산합니다.

  python fit_body_population.py body_features.csv
  python fit_body_population.py body_features.csv live_body_measurements.csv --save
  python fit_body_population.py body_features.csv --standard-share 0.25          # 표준체형 목표 25%
  python fit_body_population.py body_features.csv --sk-shares 30.8 33.2 35.9      # 작은역삼각 큰사각 사각

  ※ 정답 라벨 없이도 됩니다. label 열이 있으면 방식별 정확도도 보여줍니다.
  ※ 사람 수: 최소 15명, 권장 30명 이상. (Size Korea 구성비에 '맞추는' 방식이라 사람이 적으면 의미가 약함)
  ※ 팀 측정 대상이 Size Korea 20대 남성과 체형 분포가 비슷하다는 가정이 들어 있습니다.
"""
import argparse
import json
import math
import os
import sys
import time
from collections import Counter

import numpy as np
import pandas as pd

import body_shape as B
import config

KEYS = ["shoulder_dev", "trunk_dev"]


def load(paths, include_unusable):
    frames = []
    for p in paths:
        if not os.path.exists(p):
            print(f"[건너뜀] {p}: 파일이 없습니다. (지금 폴더: {os.getcwd()})")
            continue
        d = pd.read_csv(p, encoding="utf-8-sig")
        if any(k not in d.columns for k in KEYS):
            print(f"[건너뜀] {p}: 체형 측정값(shoulder_dev, trunk_dev) 열이 없음 — body-batch 또는 run_live_body 결과인지 확인")
            continue
        frames.append(d)
    if not frames:
        sys.exit("읽을 수 있는 체형 CSV 가 없습니다.\n"
                 "  · live_body_measurements.csv ← python run_live_body.py --height 키 --person 이름 으로 측정하면 생김\n"
                 "  · body_features.csv          ← python run_test.py body-batch 사진폴더 --labels 라벨.csv -o body_features.csv\n"
                 "  지금 폴더의 CSV 파일 확인: Windows 'dir *.csv' / Mac 'ls *.csv'")
    d = pd.concat(frames, ignore_index=True)
    if "usable" in d.columns and not include_unusable:
        d = d[d["usable"].astype(str).str.lower().isin(["true", "1"])]
    d = d.dropna(subset=KEYS).copy()
    if "person_id" not in d.columns:
        d["person_id"] = ""
    d["person_id"] = d["person_id"].fillna("").astype(str).str.strip()
    blank = d["person_id"] == ""
    d.loc[blank, "person_id"] = ["_photo_%d" % i for i in range(blank.sum())]
    # 실시간 측정은 한 번(session)에 프레임 여러 개 → 측정 1회 = 그 프레임들의 중앙값 1개로
    # (예전 형식은 프레임마다 한 줄, 새 형식은 회차마다 한 줄 → 둘 다 '회차 1개 = 1줄'로 맞춤)
    if "session" in d.columns and d["session"].notna().any():
        d["session"] = d["session"].fillna("").astype(str)
        keys = ["person_id", "session"]
        if "round" in d.columns:
            d["round"] = d["round"].fillna("").astype(str)
            keys.append("round")
        agg = {k: "median" for k in KEYS}
        if "label" in d.columns:
            agg["label"] = "first"
        d = d.groupby(keys, as_index=False).agg(agg)
    # 사람 몸으로 볼 수 없는 값(예: 어깨너비 139cm)은 잘못 잰 것 → 기준 계산에서 제외
    ok = d.apply(lambda r: B.plausible({"shoulder_dev": r["shoulder_dev"], "trunk_dev": r["trunk_dev"]}), axis=1)
    if (~ok).any():
        bad = d[~ok]
        print(f"[제외] 사람 몸으로 보기 어려운 측정 {len(bad)}개 (config.BODY_PLAUSIBLE_DEV 범위 밖):")
        for _, r in bad.iterrows():
            print(f"   {r['person_id']}  {r.get('session', '')} {r.get('round', '')}회차  "
                  f"어깨 {r['shoulder_dev']:+.2f}  몸통 {r['trunk_dev']:+.2f}")
        d = d[ok].copy()
    d["upper_taper"] = d["shoulder_dev"] - d["trunk_dev"]
    return d


def per_person(d):
    g = d.groupby("person_id")
    out = g[KEYS + ["upper_taper"]].median()
    if "label" in d.columns:
        out["label"] = g["label"].agg(lambda s: next((str(v).strip() for v in s if str(v).strip() not in ("", "nan")), ""))
    return out


def robust_spread(x):
    mad = np.median(np.abs(x - np.median(x))) * 1.4826
    return float(mad if mad > 0 else np.std(x))


FIT_N_ROUNDS = 2   # 실시간 측정 기본 회차 수 — 분포는 '2회 측정' 기준으로 계산 (fit_population.py 와 같음)


def classify_all(people, median, spread, rules, mode="relative", noise=None):
    """사람별 판정. noise 를 주면 실제 측정 화면과 같은 방식(측정 오차만큼 흔든 확률의 1위)으로 판정."""
    keep = (config.BODY_CLASSIFIER, B._BODY_POP)
    config.BODY_CLASSIFIER = mode
    B._BODY_POP = {"median": median, "spread": spread, "rules": rules, "source": "fit", "noise": noise}
    preds = []
    for _, r in people.iterrows():
        f = {"shoulder_dev": r["shoulder_dev"], "trunk_dev": r["trunk_dev"]}
        preds.append(B.probabilities(f, n_photos=FIT_N_ROUNDS)[0][0] if noise else B.classify(f))
    config.BODY_CLASSIFIER, B._BODY_POP = keep
    return preds


def measurement_noise(d):
    """같은 사람을 여러 번 잰 기록으로 1회 측정의 흔들림(표준편차) 계산. 반복 측정한 사람이 5명 미만이면 None."""
    g = d.groupby("person_id")
    rep = g.size()[g.size() >= 2].index
    if len(rep) < 5:
        return None
    dr = d[d["person_id"].isin(rep)].groupby("person_id")
    return {k: round(float(dr[k].var(ddof=1).mean()) ** 0.5, 4) for k in ("shoulder_dev", "trunk_dev")}


def calibrate_rules(people, median, spread, rules, noise, target, passes=2):
    """fit_rules 는 '한 번 판정' 기준으로 비율을 맞춤. 실제 판정은 '확률 1위'라 경계 근처 사람이 조금 다르게 갈 수 있어서,
    기준 3개를 하나씩 조금씩 움직여 '확률 1위' 판정에서도 목표 비율이 되게 다시 맞춤 (fit_population.py 의 반경 맞추기와 같은 생각)."""
    grids = [("standard_radius", "standard", np.arange(0.10, 2.51, 0.05)),
             ("large_rect_min_trunk_z", "large_rectangle", np.arange(-1.0, 3.01, 0.05)),
             ("small_inv_min_taper_z", "small_inv_triangle", np.arange(-2.0, 2.01, 0.05))]
    best_rules = dict(rules)
    for _ in range(passes):
        for key, cls, grid in grids:
            best = None
            for v in grid:
                cand = dict(best_rules)
                cand[key] = round(float(v), 3)
                share = Counter(classify_all(people, median, spread, cand, noise=noise))[cls] / len(people)
                score = (round(abs(share - target[cls]), 6), abs(v - rules[key]))   # 목표 비율에 가깝게, 같으면 원래 값에 가깝게
                if best is None or score < best[0]:
                    best = (score, cand)
            best_rules = best[1]
    return best_rules


def fit_rules(people, median, spread, standard_share, sk):
    """팀 데이터에서 유형 비율이 (표준 standard_share, 나머지는 Size Korea 구성비)가 되도록 기준을 순서대로 계산."""
    z_sh = (people["shoulder_dev"] - median["shoulder_dev"]) / spread["shoulder_dev"]
    z_tr = (people["trunk_dev"] - median["trunk_dev"]) / spread["trunk_dev"]
    z_tp = (people["upper_taper"] - median["upper_taper"]) / spread["upper_taper"]
    r = np.hypot(z_sh, z_tr).to_numpy()
    small, large, rect = (np.array(sk) / np.sum(sk)).tolist()
    R = float(np.quantile(r, standard_share)) if standard_share > 0 else -1.0
    rest = r > R
    T_large = float(np.quantile(z_tr[rest], 1 - large)) if rest.sum() else 1e9
    rest2 = rest & (z_tr.to_numpy() < T_large)
    T_small = float(np.quantile(z_tp[rest2], 1 - small / (small + rect))) if rest2.sum() else 1e9
    return {"standard_radius": round(R, 3), "large_rect_min_trunk_z": round(T_large, 3),
            "small_inv_min_taper_z": round(T_small, 3)}


def fmt(preds):
    c, n = Counter(preds), len(preds)
    return "  ".join(f"{config.BODY_LABELS[k]} {c.get(k, 0) / n:4.0%}" for k in config.BODY_LABELS)


def accuracy(people, preds):
    if "label" not in people.columns:
        return None
    ko2key = {v: k for k, v in config.BODY_LABELS.items()}
    lab = people["label"].map(lambda v: ko2key.get(str(v).strip(), str(v).strip()))
    m = lab.isin(config.BODY_LABELS.keys()).to_numpy()
    return (float((np.array(preds)[m] == lab.to_numpy()[m]).mean()), int(m.sum())) if m.sum() else None


def main():
    ap = argparse.ArgumentParser(description="평균 체형 기준 맞추기")
    ap.add_argument("csv", nargs="+", help="체형 측정값 CSV (body-batch 결과, live_body_measurements.csv)")
    ap.add_argument("--standard-share", type=float, default=0.20, help="표준체형 목표 비율 (기본 0.20)")
    ap.add_argument("--sk-shares", type=float, nargs=3, default=[30.8, 33.2, 35.9],
                    metavar=("작은역삼각", "큰사각", "사각"), help="나머지 세 유형 비율 (기본: Size Korea 20대 남성)")
    ap.add_argument("--include-unusable", action="store_true")
    ap.add_argument("--save", action="store_true", help="body_population.json 으로 저장 (이후 자동 적용)")
    a = ap.parse_args()

    d = load(a.csv, a.include_unusable)
    people = per_person(d)
    n = len(people)
    print(f"측정 {len(d)}회 → {n}명 기준으로 계산 (사람별 중앙값)")
    if n < 15:
        print(f"⚠ 사람이 {n}명뿐입니다. 비율에 맞춘 기준이 불안정할 수 있어요 (최소 15명, 권장 30명 이상).")

    median = {k: float(people[k].median()) for k in KEYS + ["upper_taper"]}
    spread = {k: max(robust_spread(people[k].to_numpy()), 1e-3) for k in KEYS + ["upper_taper"]}
    noise = measurement_noise(d)
    if noise:
        print(f"측정 오차 (같은 사람 1회 측정의 흔들림): 어깨 {noise['shoulder_dev']:.3f}, 몸통 {noise['trunk_dev']:.3f}")
    else:
        noise = dict(config.BODY_FEATURE_NOISE)
        print(f"반복 측정한 사람이 5명 미만이라 측정 오차는 config.BODY_FEATURE_NOISE 값을 씁니다: {noise}")

    print("\n측정 치우침 진단 (Size Korea 20대 남성 평균 = 0%)")
    print(f"  팀 데이터 중앙값: 어깨 폭 {median['shoulder_dev']:+.1%}, 몸통 굵기 {median['trunk_dev']:+.1%}")
    if median["trunk_dev"] > 0.04:
        print("  → 몸통이 평균보다 굵게 재지는 경향: 옷 두께·팔 영역 영향일 가능성. 예전 규칙에서 '큰 사각체형'이 많고"
              " '작은 역삼각·표준'이 적었던 원인입니다.")
    if median["shoulder_dev"] < -0.04 and median["trunk_dev"] < -0.04:
        print("  → 어깨·몸통 모두 작게 재지는 경향: 사진 속 키(머리카락·신발 포함)가 입력한 키보다 커서일 수 있음.")

    sk_tot = sum(a.sk_shares)
    target = {"standard": a.standard_share, "small_inv_triangle": (1 - a.standard_share) * a.sk_shares[0] / sk_tot,
              "large_rectangle": (1 - a.standard_share) * a.sk_shares[1] / sk_tot,
              "rectangle": (1 - a.standard_share) * a.sk_shares[2] / sk_tot}
    first = fit_rules(people, median, spread, a.standard_share, a.sk_shares)
    rules = calibrate_rules(people, median, spread, first, noise, target)
    rows = [
        ("예전 규칙 (Size Korea 절대 기준)", classify_all(people, median, spread, first, "rules")),
        ("상대 판정 · 임시 기준", classify_all(people, config.BODY_POP_MEDIAN, config.BODY_POP_SPREAD, config.BODY_REL_RULES)),
        ("상대 판정 · 팀 기준 (한 번 판정)", classify_all(people, median, spread, first)),
        ("상대 판정 · 팀 기준 (확률 1위) ← 실제", classify_all(people, median, spread, rules, noise=noise)),
    ]
    print("\n유형 분포 비교 (사람 기준)")
    print("-" * 96)
    print(f"{'목표':34s} " + "  ".join(f"{config.BODY_LABELS[k]} {target[k]:4.0%}" for k in config.BODY_LABELS))
    for name, preds in rows:
        acc = accuracy(people, preds)
        print(f"{name:34s} {fmt(preds)}" + (f"   정확도 {acc[0]:.0%} ({acc[1]}명)" if acc else ""))
    print("-" * 96)
    print(f"\n계산된 기준 (확률 1위·{FIT_N_ROUNDS}회 측정 기준으로 다시 맞춤):", rules)
    print("  (한 번 판정 기준으로 처음 계산한 값:", first, ")")
    print(f"팀 평균 체형: {json.dumps({k: round(v, 4) for k, v in median.items()})}")
    print(f"사람 간 퍼짐: {json.dumps({k: round(v, 4) for k, v in spread.items()})}")

    if a.save:
        out = {"median": median, "spread": spread, "rules": rules, "noise": noise, "n_people": n,
               "standard_share": a.standard_share, "sk_shares": a.sk_shares, "sources": a.csv,
               "created": time.strftime("%Y-%m-%d %H:%M")}
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "body_population.json")
        with open(path, "w", encoding="utf-8") as fp:
            json.dump(out, fp, ensure_ascii=False, indent=2)
        print(f"\n저장: {path}\n→ run_test.py / run_live_body.py 가 이 기준을 자동으로 씁니다. 되돌리려면 이 파일을 지우세요.")
    else:
        print("\n적용하려면 --save 를 붙여 다시 실행하세요.")


if __name__ == "__main__":
    main()
