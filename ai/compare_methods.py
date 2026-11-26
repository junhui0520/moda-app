"""
비교 실험: 사진 1장 판정 vs 여러 장(3장·5장) 종합 판정, 어느 쪽이 더 정확하고 일관적인가?

준비: 사람마다 폴더 하나씩 (폴더 이름 = person_id)
    photos/face_exp/
    ├─ kim/   kim_01.jpg ... kim_08.jpg
    ├─ lee/   ...
    └─ park/  ...
  (선택) 정답 파일 person_labels.csv
    person_id,label
    kim,계란형
    lee,직사각형

실행:
    python compare_methods.py photos/face_exp --labels person_labels.csv

  - 처음 한 번은 사진마다 측정하느라 시간이 걸리고, 결과를 <폴더>/_measurements.csv 에 저장해 둡니다.
    다음부터는 이 파일을 재사용합니다 (사진을 바꿨다면 --refresh).
  - face-batch 로 이미 만든 CSV(person_id 열 필요)를 바로 넣어도 됩니다:
    python compare_methods.py face_features.csv --labels person_labels.csv

옵션:
    --k 1 3 5          비교할 장수 (기본 1 3 5)
    --draws 300        사람마다 무작위로 사진 조합을 뽑는 횟수
    --include-unusable 품질 검사(고개 각도·표정)에 걸린 사진도 포함
    -o compare_out     결과 저장 폴더

비교하는 방식
    1장           : 사진 한 장씩 따로 판정
    k장 중앙값    : k장의 측정값 중앙값으로 판정 (키트의 face-multi 방식)
    k장 투표      : k장을 각각 판정한 뒤 다수결 (비교용)

지표
    정확도        : 정답(label)과 같은 판정이 나온 비율 — 정답 파일이 있을 때만
    일관성        : 같은 사람에서 '가장 많이 나온 판정'과 같은 결과가 나온 비율
    흔들림        : 같은 사람 안에서 length_ratio 값의 표준편차 (작을수록 안정)
    ※ 사람마다 사진 수가 달라도 공정하도록 '사람별로 계산한 뒤 평균'을 냅니다.
"""
import argparse
import csv
import itertools
import os
import sys
from collections import Counter

os.environ.setdefault("GLOG_minloglevel", "2")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import numpy as np
import pandas as pd

import config
from face_shape import FEATURE_KEYS, classify

IMG_EXT = (".jpg", ".jpeg", ".png", ".webp", ".bmp")


# ---------------------------------------------------------------------------
# 1) 사진별 측정값 준비
# ---------------------------------------------------------------------------
def measure_folder(root, refresh):
    cache = os.path.join(root, "_measurements.csv")
    if os.path.exists(cache) and not refresh:
        print(f"저장된 측정값 사용: {cache}  (사진을 바꿨다면 --refresh)")
        return pd.read_csv(cache, encoding="utf-8-sig")

    from face_shape import analyze_face
    people = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)) and not d.startswith("_"))
    if not people:
        sys.exit(f"{root} 안에 사람별 폴더가 없습니다. (예: {root}/kim/kim_01.jpg)")
    rows = []
    total = sum(len([f for f in os.listdir(os.path.join(root, p)) if f.lower().endswith(IMG_EXT)]) for p in people)
    done = 0
    for pid in people:
        for name in sorted(os.listdir(os.path.join(root, pid))):
            if not name.lower().endswith(IMG_EXT):
                continue
            done += 1
            path = os.path.join(root, pid, name)
            try:
                r, _ = analyze_face(path, annotate=False)
            except Exception as e:
                r = {"ok": False, "error": repr(e)}
            mark = "✓" if r.get("ok") and r.get("usable") else ("△" if r.get("ok") else "✗")
            print(f"[{done}/{total}] {mark} {pid}/{name}", flush=True)
            if not r.get("ok"):
                continue
            rows.append({"person_id": pid, "file": name, **r["features"],
                         "predicted": r["face_shape_key"], "usable": r["usable"],
                         "warnings": " / ".join(r.get("warnings", []))})
    df = pd.DataFrame(rows)
    df.to_csv(cache, index=False, encoding="utf-8-sig")
    print(f"측정값 저장: {cache}  (✓ 사용 가능 / △ 품질 미달 / ✗ 얼굴 인식 실패)\n")
    return df


def load_labels(path):
    if not path:
        return {}
    ko2key = {v: k for k, v in config.FACE_LABELS.items()}
    out = {}
    with open(path, encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            lab = (row.get("label") or "").strip()
            if row.get("person_id") and lab:
                out[row["person_id"].strip()] = ko2key.get(lab, lab)
    bad = {v for v in out.values() if v not in config.FACE_LABELS}
    if bad:
        sys.exit(f"정답 파일에 알 수 없는 유형이 있습니다: {bad}\n가능한 값: {list(config.FACE_LABELS.values())}")
    return out


# ---------------------------------------------------------------------------
# 2) 방식별 판정 시뮬레이션
# ---------------------------------------------------------------------------
def subsets(n, k, draws, rng):
    """n장 중 k장 조합 — 전체 조합 수가 draws 이하이면 전부, 아니면 무작위 draws개."""
    combos = list(itertools.combinations(range(n), k)) if _ncr(n, k) <= draws else None
    if combos is not None:
        return combos
    return [tuple(rng.choice(n, k, replace=False)) for _ in range(draws)]


def _ncr(n, k):
    from math import comb
    return comb(n, k)


def vote(labels):
    """다수결. 동점이면 각 판정의 '평균 측정값'에 가까운 쪽 대신 단순히 먼저 나온 것 — 투표 방식의 약점."""
    return Counter(labels).most_common(1)[0][0]


def evaluate(df, labels, ks, draws, seed):
    rng = np.random.default_rng(seed)
    max_k = max(ks)
    counts = df.groupby("person_id").size()
    eligible = counts[counts >= max_k].index.tolist()
    skipped = counts[counts < max_k]
    if not eligible:
        sys.exit(f"사진이 {max_k}장 이상인 사람이 없습니다. 사람마다 {max_k}장 이상(권장 8장 이상) 찍어 주세요.")

    methods = [("1장", 1, "single")]
    for k in ks:
        if k > 1:
            methods += [(f"{k}장 중앙값", k, "median"), (f"{k}장 투표", k, "vote")]

    per_person = []
    for pid in eligible:
        g = df[df["person_id"] == pid].reset_index(drop=True)
        X = g[FEATURE_KEYS].to_numpy(float)
        single_pred = [classify(dict(zip(FEATURE_KEYS, x))) for x in X]
        truth = labels.get(pid)
        row = {"person_id": pid, "정답": config.FACE_LABELS.get(truth, ""), "사용 사진 수": len(g)}
        for name, k, how in methods:
            if how == "single":
                preds = single_pred
                lengths = X[:, 0]
            else:
                preds, lengths = [], []
                for idx in subsets(len(g), k, draws, rng):
                    idx = list(idx)
                    if how == "median":
                        med = np.median(X[idx], axis=0)
                        preds.append(classify(dict(zip(FEATURE_KEYS, med))))
                        lengths.append(med[0])
                    else:
                        preds.append(vote([single_pred[i] for i in idx]))
                        lengths.append(np.nan)
            c = Counter(preds)
            mode, mode_n = c.most_common(1)[0]
            row[f"{name}|일관성"] = mode_n / len(preds)
            row[f"{name}|대표 판정"] = config.FACE_LABELS[mode]
            row[f"{name}|흔들림"] = float(np.nanstd(lengths)) if how != "vote" else np.nan
            if truth:
                row[f"{name}|정확도"] = sum(p == truth for p in preds) / len(preds)
        per_person.append(row)

    detail = pd.DataFrame(per_person)
    summary = []
    for name, k, how in methods:
        s = {"방식": name, "장수": k,
             "일관성": detail[f"{name}|일관성"].mean(),
             "흔들림(length_ratio 표준편차)": detail[f"{name}|흔들림"].mean() if how != "vote" else np.nan}
        if labels and f"{name}|정확도" in detail:
            s["정확도"] = detail[f"{name}|정확도"].mean()
        summary.append(s)
    return pd.DataFrame(summary), detail, eligible, skipped


# ---------------------------------------------------------------------------
# 3) 결과 저장 (CSV · 차트 · 보고서)
# ---------------------------------------------------------------------------
def save_chart(summary, path, has_labels):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib import font_manager
    except ImportError:
        print("(matplotlib 이 없어 차트는 건너뜀: pip install matplotlib)")
        return None
    # 한글 폰트: 설치된 것 중 첫 번째 사용
    for fam in ["Malgun Gothic", "AppleGothic", "NanumGothic", "Noto Sans CJK KR", "Noto Sans KR", "Noto Sans CJK TC"]:
        if any(fam == f.name for f in font_manager.fontManager.ttflist):
            plt.rcParams["font.family"] = fam
            break
    plt.rcParams["axes.unicode_minus"] = False

    metrics = (["정확도"] if has_labels else []) + ["일관성"]
    fig, axes = plt.subplots(1, len(metrics), figsize=(5.2 * len(metrics), 4), squeeze=False)
    colors = ["#8A94A6" if n == "1장" else ("#2F6FDE" if "중앙값" in n else "#B9C7E6") for n in summary["방식"]]
    for ax, m in zip(axes[0], metrics):
        vals = summary[m].to_numpy()
        bars = ax.bar(summary["방식"], vals * 100, color=colors, width=0.62)
        for b, v in zip(bars, vals):
            ax.text(b.get_x() + b.get_width() / 2, v * 100 + 1.5, f"{v:.0%}", ha="center", fontsize=10)
        ax.set_ylim(0, 110)
        ax.set_title(m, fontsize=12, fontweight="bold")
        ax.set_ylabel("%")
        ax.spines[["top", "right"]].set_visible(False)
        ax.tick_params(axis="x", labelsize=9)
    fig.suptitle("얼굴형 판정: 사진 1장 vs 여러 장 종합", fontsize=13)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)
    return path


def _md_table(df):
    cols = list(df.columns)
    out = ["| " + " | ".join(cols) + " |", "|" + "|".join("---" for _ in cols) + "|"]
    for _, r in df.iterrows():
        out.append("| " + " | ".join(str(r[c]) for c in cols) + " |")
    return "\n".join(out)


def write_report(summary, detail, eligible, skipped, labels, args, path, chart):
    fmt = summary.copy()
    for c in ["정확도", "일관성"]:
        if c in fmt:
            fmt[c] = fmt[c].map(lambda v: f"{v:.0%}")
    col = "흔들림(length_ratio 표준편차)"
    fmt[col] = fmt[col].map(lambda v: "-" if pd.isna(v) else f"{v:.4f}")
    lines = [
        "# 얼굴형 판정 비교 실험 결과", "",
        f"- 대상: {len(eligible)}명 (사진 {int(detail['사용 사진 수'].sum())}장)"
        + (f", 사진이 부족해 제외된 사람 {len(skipped)}명" if len(skipped) else ""),
        f"- 사진 조합: 사람마다 최대 {args.draws}회 무작위 추출 (seed={args.seed})",
        f"- 품질 미달 사진: {'포함' if args.include_unusable else '제외'}",
        f"- 정답 라벨: {'있음 (' + str(len(labels)) + '명)' if labels else '없음 → 일관성만 비교'}",
        "", "## 요약", "", _md_table(fmt), "",
    ]
    if chart:
        lines += [f"![chart]({os.path.basename(chart)})", ""]
    lines += [
        "## 읽는 법", "",
        "- **일관성**: 같은 사람을 다시 판정했을 때 같은 결과가 나올 확률에 가깝습니다. 높을수록 결과가 안 흔들림.",
        "- **정확도**: 정답 라벨과 일치한 비율. 일관성은 높은데 정확도가 낮다면 → 측정은 안정적이지만 판정 기준값(config.py)이 틀린 것.",
        "- **흔들림**: 판정 직전 측정값(length_ratio)의 사람 내 표준편차. 여러 장 중앙값에서 작아지는 정도가 개선 효과입니다.",
        "- **중앙값 vs 투표**: 같은 장수에서 중앙값이 투표보다 높으면, '측정값을 합친 뒤 판정'하는 방식이 더 낫다는 근거입니다.",
        "", "## 사람별 결과", "", "`compare_detail.csv` 참고. 일관성이 낮은 사람은 경계 얼굴이거나 촬영 조건이 제각각인 경우입니다.",
    ]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    ap = argparse.ArgumentParser(description="1장 vs 여러 장 판정 비교 실험")
    ap.add_argument("source", help="사람별 폴더가 들어 있는 폴더, 또는 측정값 CSV")
    ap.add_argument("--labels", help="정답 파일 (person_id,label)")
    ap.add_argument("--k", type=int, nargs="+", default=[1, 3, 5])
    ap.add_argument("--draws", type=int, default=300)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--include-unusable", action="store_true")
    ap.add_argument("--refresh", action="store_true", help="저장된 측정값을 무시하고 다시 측정")
    ap.add_argument("-o", "--out", default="compare_out")
    args = ap.parse_args()

    if os.path.isdir(args.source):
        df = measure_folder(args.source, args.refresh)
    else:
        df = pd.read_csv(args.source, encoding="utf-8-sig")
    if "person_id" not in df.columns or df["person_id"].isna().all():
        sys.exit("person_id 정보가 없습니다. 사람별 폴더 구조로 넣거나, CSV 에 person_id 열을 채워 주세요.")
    df = df[df["person_id"].notna()].copy()
    df["person_id"] = df["person_id"].astype(str).str.strip()
    df = df[df["person_id"] != ""]
    missing = [k for k in FEATURE_KEYS if k not in df.columns]
    if missing:
        sys.exit(f"CSV 에 측정값 열이 없습니다: {missing} (face-batch 로 만든 얼굴형 CSV 인지 확인)")

    n_all = len(df)
    df_all_people = df["person_id"].unique().tolist()
    if "usable" in df.columns and not args.include_unusable:
        df = df[df["usable"].astype(str).str.lower().isin(["true", "1"])]
    print(f"측정 성공 {n_all}장 중 {len(df)}장 사용 ({'품질 미달 포함' if args.include_unusable else '품질 미달 제외'})")

    labels = load_labels(args.labels)
    # 사진별 label 열만 있는 CSV 라면 사람별 정답으로 변환
    if not labels and "label" in df.columns:
        ko2key = {v: k for k, v in config.FACE_LABELS.items()}
        lab = df.dropna(subset=["label"]).groupby("person_id")["label"].agg(lambda s: s.mode().iloc[0])
        labels = {p: ko2key.get(str(v).strip(), str(v).strip()) for p, v in lab.items() if str(v).strip()}

    ks = sorted(set(args.k) | {1})
    all_people = sorted(set(df_all_people))
    summary, detail, eligible, skipped = evaluate(df, labels, ks, args.draws, args.seed)
    # 품질 미달로 사진이 하나도 안 남은 사람도 '제외'에 표시
    for p in all_people:
        if p not in eligible and p not in skipped.index:
            skipped.loc[p] = 0

    os.makedirs(args.out, exist_ok=True)
    summary.to_csv(os.path.join(args.out, "compare_summary.csv"), index=False, encoding="utf-8-sig")
    detail.to_csv(os.path.join(args.out, "compare_detail.csv"), index=False, encoding="utf-8-sig")
    chart = save_chart(summary, os.path.join(args.out, "compare_chart.png"), bool(labels))
    write_report(summary, detail, eligible, skipped, labels, args, os.path.join(args.out, "compare_report.md"), chart)

    print(f"\n대상 {len(eligible)}명" + (f" (사진 {max(ks)}장 미만이라 제외: {', '.join(skipped.index)})" if len(skipped) else ""))
    show = summary.copy()
    for c in ["정확도", "일관성"]:
        if c in show:
            show[c] = show[c].map(lambda v: f"{v:.0%}")
    show["흔들림(length_ratio 표준편차)"] = show["흔들림(length_ratio 표준편차)"].map(
        lambda v: "-" if pd.isna(v) else f"{v:.4f}")
    print(show.to_string(index=False))
    if not labels:
        print("\n※ 정답 파일이 없어 정확도는 계산하지 않았습니다 (--labels person_labels.csv)")
    print(f"\n저장: {args.out}/compare_report.md, compare_summary.csv, compare_detail.csv"
          + (", compare_chart.png" if chart else ""))


if __name__ == "__main__":
    main()
