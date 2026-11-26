"""
평균 얼굴 기준값 맞추기 — '계란형이 너무 많이 나온다' 문제 해결용

얼굴형 판정(relative 방식)은 "평균 얼굴과 비교해 어디가 두드러지는지"로 유형을 정합니다.
그런데 config.py 의 평균값은 테스트 얼굴 몇 명으로 잡은 임시값이라, 실제 사용자와 다르면
대부분이 '평균형 = 계란형'으로 나오거나 반대로 한쪽 유형으로 쏠립니다.
이 스크립트는 팀이 모은 측정값으로 평균·퍼짐 정도를 다시 잡고,
계란형 비율이 원하는 수준이 되도록 기준(FACE_OVAL_RADIUS)을 추천합니다.

  python fit_population.py face_features.csv
  python fit_population.py face_features.csv live_measurements.csv photos/face_exp/_measurements.csv
  python fit_population.py face_features.csv --oval-share 0.3       # 계란형 목표 비율 30%
  python fit_population.py face_features.csv --save                 # face_population.json 으로 저장 → 자동 적용

  ※ 정답 라벨이 없어도 됩니다 (얼굴 측정값만 있으면 됨).
  ※ 같은 사람을 여러 번 잰 데이터(run_live.py --person 으로 쌓인 live_measurements.csv 등)가 있으면
     특징별 '신뢰도'와 '회차 간 흔들림'도 계산해서, 잘 흔들리는 특징은 판정에서 덜 반영하게 합니다.
  ※ 사람 수가 많을수록 좋습니다. 최소 15명, 권장 30명 이상.
  ※ label 열이 채워져 있으면 방식별 정확도도 함께 보여줍니다.
"""
import argparse
import json
import os
import sys
import time
from collections import Counter

import numpy as np
import pandas as pd

import config
import face_shape as F

K = F.FEATURE_KEYS
FIT_N_ROUNDS = 2   # 실시간 측정 기본 회차 수 (LIVE_ROUNDS="auto" → 보통 2회). 분포는 '2회 측정' 기준으로 계산


def _final(f, n_rounds=FIT_N_ROUNDS):
    """run_live.py 와 똑같은 최종 판정 = 측정 오차만큼 흔들어 본 확률의 1위."""
    return F.summarize(f, n_photos=n_rounds)["face_shape_key"]


def load(paths, include_unusable):
    frames = []
    for p in paths:
        if not os.path.exists(p):
            print(f"[건너뜀] {p}: 파일이 없습니다. (지금 폴더: {os.getcwd()})")
            continue
        d = pd.read_csv(p, encoding="utf-8-sig")
        miss = [k for k in K if k not in d.columns]
        if miss:
            print(f"[건너뜀] {p}: 얼굴형 측정값 열이 없음 {miss}")
            continue
        d["_src"] = os.path.basename(p)
        frames.append(d)
    if not frames:
        sys.exit("읽을 수 있는 얼굴형 CSV 가 없습니다.\n"
                 "  · live_measurements.csv  ← python run_live.py --person 이름 으로 측정하면 생김\n"
                 "  · face_features.csv      ← python run_test.py face-batch 사진폴더 -o face_features.csv 로 만들면 생김\n"
                 "  지금 폴더의 CSV 파일 확인: Windows 'dir *.csv' / Mac 'ls *.csv'")
    d = pd.concat(frames, ignore_index=True)
    if "usable" in d.columns and not include_unusable:
        d = d[d["usable"].astype(str).str.lower().isin(["true", "1"])]
    d = d.dropna(subset=K)
    if "person_id" not in d.columns:
        d["person_id"] = ""
    d["person_id"] = d["person_id"].fillna("").astype(str).str.strip()
    # person_id 가 없는 행은 사진 한 장 = 한 사람으로 취급
    d.loc[d["person_id"] == "", "person_id"] = ["_photo_%d" % i for i in range((d["person_id"] == "").sum())]
    return d


def per_person(d):
    """사람마다 사진 수가 달라도 공평하게: 사람별 중앙값 1개씩."""
    g = d.groupby("person_id")
    out = g[K].median()
    if "label" in d.columns:
        lab = g["label"].agg(lambda s: s.dropna().astype(str).str.strip().replace("", np.nan).dropna().mode().iloc[0]
                             if s.dropna().astype(str).str.strip().ne("").any() else "")
        out["label"] = lab
    return out


def robust_spread(x):
    mad = np.median(np.abs(x - np.median(x))) * 1.4826
    return float(mad if mad > 0 else np.std(x))


def distribution(people, median, spread, radius, mode="relative", weights=None, noise=None):
    """사람별 최종 판정 목록. 실제 측정 화면과 같은 방식(확률 1위)으로 판정."""
    keep = (config.FACE_CLASSIFIER, F._POP, config.FACE_OVAL_RADIUS, config.FACE_FEATURE_NOISE)
    config.FACE_CLASSIFIER = mode
    F._POP = {"median": median, "spread": spread, "source": "fit",
              "weights": weights or dict(config.FACE_FEATURE_WEIGHTS)}
    config.FACE_OVAL_RADIUS = radius
    if noise:
        config.FACE_FEATURE_NOISE = noise
    preds = [_final(row[K].to_dict()) for _, row in people.iterrows()]
    config.FACE_CLASSIFIER, F._POP, config.FACE_OVAL_RADIUS, config.FACE_FEATURE_NOISE = keep
    return preds


def reliability(d):
    """같은 사람 반복 측정으로 특징별 신뢰도 계산.
    within = 같은 사람 안의 흔들림, between = 사람끼리의 실제 차이.
    신뢰도 = between² / (between² + within²)  (1에 가까울수록 측정이 믿을 만함)"""
    g = d.groupby("person_id")
    sizes = g.size()
    rep = sizes[sizes >= 2].index
    if len(rep) < 5:
        return None
    dr = d[d["person_id"].isin(rep)]
    gr = dr.groupby("person_id")
    n_bar = float(gr.size().mean())
    out = {}
    for k in K:
        within_var = float(gr[k].var(ddof=1).mean())
        means = gr[k].mean()
        between_var = max(float(means.var(ddof=1)) - within_var / n_bar, 1e-12)
        out[k] = {"within": within_var ** 0.5, "between": between_var ** 0.5,
                  "icc": between_var / (between_var + within_var)}
    return {"per_feature": out, "n_people": len(rep), "rows_per_person": n_bar}


def consistency(d, median, spread, radius, weights, k_rounds, max_combos=30, seed=0, noise=None):
    """반복 측정이 있는 사람들에 대해 'k회 측정 중앙값' 판정이 얼마나 일관적인지 예측 (판정 = 확률 1위)."""
    import itertools
    rng = np.random.default_rng(seed)
    keep = (config.FACE_CLASSIFIER, F._POP, config.FACE_OVAL_RADIUS, config.FACE_FEATURE_NOISE)
    config.FACE_CLASSIFIER = "relative"
    F._POP = {"median": median, "spread": spread, "source": "fit", "weights": weights}
    config.FACE_OVAL_RADIUS = radius
    if noise:
        config.FACE_FEATURE_NOISE = noise
    scores = []
    for _, g in d.groupby("person_id"):
        X = g[K].to_numpy(float)
        if len(X) < max(2, k_rounds):
            continue
        combos = list(itertools.combinations(range(len(X)), k_rounds))
        if len(combos) > max_combos:
            combos = [combos[i] for i in rng.choice(len(combos), max_combos, replace=False)]
        preds = [_final(dict(zip(K, np.median(X[list(c)], axis=0))), k_rounds) for c in combos]
        scores.append(Counter(preds).most_common(1)[0][1] / len(preds))
    config.FACE_CLASSIFIER, F._POP, config.FACE_OVAL_RADIUS, config.FACE_FEATURE_NOISE = keep
    return float(np.mean(scores)) if scores else None


def fmt_dist(preds):
    c = Counter(preds)
    n = len(preds)
    return "  ".join(f"{config.FACE_LABELS[k]} {c.get(k, 0) / n:4.0%}" for k in config.FACE_LABELS)


def accuracy(people, preds):
    if "label" not in people.columns:
        return None
    ko2key = {v: k for k, v in config.FACE_LABELS.items()}
    lab = people["label"].map(lambda v: ko2key.get(str(v).strip(), str(v).strip()))
    mask = lab.isin(config.FACE_LABELS.keys()).to_numpy()
    if mask.sum() == 0:
        return None
    return float((np.array(preds)[mask] == lab.to_numpy()[mask]).mean()), int(mask.sum())


def main():
    ap = argparse.ArgumentParser(description="평균 얼굴 기준값 맞추기")
    ap.add_argument("csv", nargs="+", help="얼굴형 측정값 CSV (face-batch 결과, live_measurements.csv, _measurements.csv)")
    ap.add_argument("--oval-share", type=float, default=0.25, help="계란형 목표 비율 (기본 0.25 = 25%%)")
    ap.add_argument("--include-unusable", action="store_true", help="품질 미달 사진도 포함")
    ap.add_argument("--save", action="store_true", help="face_population.json 으로 저장 (이후 자동 적용)")
    a = ap.parse_args()

    d = load(a.csv, a.include_unusable)
    people = per_person(d)
    n = len(people)
    print(f"측정값 {len(d)}개 → {n}명 기준으로 계산 (사람별 중앙값)")
    if n < 15:
        print(f"⚠ 사람이 {n}명뿐입니다. 평균이 불안정할 수 있어요 (최소 15명, 권장 30명 이상).")

    median = {k: float(people[k].median()) for k in K}
    spread = {k: robust_spread(people[k].to_numpy()) for k in K}

    rel = reliability(d)
    weights, noise = dict(config.FACE_FEATURE_WEIGHTS), None
    if rel:
        pf = rel["per_feature"]
        # 사람 간 퍼짐은 측정 흔들림을 뺀 '실제 차이'로, 신뢰도는 0.2~1 범위로
        spread = {k: max(pf[k]["between"], 1e-6) for k in K}
        weights = {k: round(float(np.clip(pf[k]["icc"], 0.2, 1.0)), 3) for k in K}
        noise = {k: round(pf[k]["within"], 4) for k in K}
        print(f"반복 측정 데이터: {rel['n_people']}명 (1인당 평균 {rel['rows_per_person']:.1f}회) → 특징별 신뢰도 계산")
    else:
        print("같은 사람을 2번 이상 잰 사람이 5명 미만이라 신뢰도는 계산하지 않음 (config.py 임시 가중치 사용)."
              "\n  → run_live.py --person 이름 으로 사람마다 여러 번 측정하면 계산됩니다.")

    # 계란형 목표 비율에 가장 가까운 기준 찾기
    grid = np.round(np.arange(0.2, 2.51, 0.05), 2)
    shares = []
    for r in grid:
        p = distribution(people, median, spread, r, weights=weights, noise=noise)
        shares.append(p.count("oval") / n)
    best_r = float(grid[int(np.argmin([abs(s - a.oval_share) for s in shares]))])

    cur_pop = F.population()
    rows = [
        ("지금 고정 규칙 (rules)", distribution(people, cur_pop["median"], cur_pop["spread"], config.FACE_OVAL_RADIUS, "rules")),
        (f"평균 대비 · 임시 평균 (반경 {config.FACE_OVAL_RADIUS})",
         distribution(people, config.FACE_POP_MEDIAN, config.FACE_POP_SPREAD, config.FACE_OVAL_RADIUS)),
        (f"평균 대비 · 팀 데이터 평균 (반경 {best_r}) ← 추천",
         distribution(people, median, spread, best_r, weights=weights, noise=noise)),
    ]
    print("\n유형 분포 비교 (사람 기준)")
    print("-" * 100)
    for name, preds in rows:
        acc = accuracy(people, preds)
        extra = f"   정확도 {acc[0]:.0%} ({acc[1]}명)" if acc else ""
        print(f"{name:42s} {fmt_dist(preds)}{extra}")
    print("-" * 100)

    print(f"\n계란형 기준(반경)에 따른 계란형 비율 (실제 측정 화면과 같은 '확률 1위' 판정, {FIT_N_ROUNDS}회 측정 기준):")
    for r, s in zip(grid, shares):
        if r in (0.4, 0.6, 0.8, 1.0, 1.2, 1.5, 2.0) or r == best_r:
            print(f"  반경 {r:4.2f} → 계란형 {s:4.0%}" + ("   ← 목표에 가장 가까움" if r == best_r else ""))

    print("\n팀 데이터 평균 얼굴:")
    for k in K:
        line = f"  {k:15s} 중앙값 {median[k]:8.3f}   사람 간 퍼짐 {spread[k]:.4f}"
        if rel:
            pf = rel["per_feature"][k]
            line += f"   같은 사람 흔들림 {pf['within']:.4f}   신뢰도 {pf['icc']:.2f} → 가중치 {weights[k]:.2f}"
        print(line)

    if rel:
        print("\n같은 사람 일관성 예측 (반복 측정이 있는 사람 기준, 같은 결과가 나올 비율):")
        ones = {k: 1.0 for k in K}
        for kr in (1, 2, 3):
            if rel["rows_per_person"] < kr:
                continue
            before = consistency(d, median, spread, best_r, ones, kr, noise=noise)
            after = consistency(d, median, spread, best_r, weights, kr, noise=noise)
            if before is not None:
                print(f"  {kr}회 측정 중앙값:  가중치 없음 {before:4.0%}  →  신뢰도 가중치 {after:4.0%}")

    if a.save:
        out = {"median": median, "spread": spread, "oval_radius": best_r, "n_people": n,
               "weights": weights, "noise": noise,
               "oval_share_target": a.oval_share, "sources": a.csv, "created": time.strftime("%Y-%m-%d %H:%M")}
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "face_population.json")
        with open(path, "w", encoding="utf-8") as fp:
            json.dump(out, fp, ensure_ascii=False, indent=2)
        print(f"\n저장: {path}\n→ 이제 run_test.py / run_live.py / compare_methods.py 가 이 기준을 자동으로 씁니다."
              "\n  되돌리려면 이 파일을 지우세요.")
    else:
        print("\n적용하려면 --save 를 붙여 다시 실행하세요.")


if __name__ == "__main__":
    main()
