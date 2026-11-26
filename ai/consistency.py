"""
재현성(일관성) 테스트: 같은 사람을 여러 번 찍었을 때 결과가 흔들리지 않는지 확인.
정답 라벨이 없어도 할 수 있는 첫 테스트입니다.

  python consistency.py face_features.csv
  python consistency.py body_features.csv

CSV 의 person_id 열(labels CSV 에서 넣음)로 사람을 묶습니다.
"""
import sys

import pandas as pd

from calibrate import BODY_FEATURES, FACE_FEATURES


def main(path):
    df = pd.read_csv(path, encoding="utf-8-sig")
    df["person_id"] = df.get("person_id", pd.Series(dtype=str)).fillna("").astype(str).str.strip()
    df = df[df["person_id"] != ""]
    if df.empty:
        sys.exit("person_id 가 비어 있습니다. labels CSV 에 person_id 를 적고 batch 를 다시 돌리세요.")
    feats = FACE_FEATURES if "length_ratio" in df.columns else BODY_FEATURES
    if "usable" in df.columns and "--all" not in sys.argv:
        n_bad = (~df["usable"].astype(bool)).sum()
        df = df[df["usable"].astype(bool)]
        print(f"촬영 품질 미달 {n_bad}장 제외 (포함하려면 --all)")

    print(f"{df['person_id'].nunique()}명, {len(df)}장\n")
    rows = []
    for pid, g in df.groupby("person_id"):
        top = g["predicted"].value_counts()
        rows.append({
            "person_id": pid,
            "사진 수": len(g),
            "가장 많이 나온 판정": top.index[0],
            "일치율": round(top.iloc[0] / len(g), 2),
            "판정 분포": ", ".join(f"{k}:{v}" for k, v in top.items()),
            **{f"{f} 표준편차": round(g[f].std(ddof=0), 3) for f in feats},
        })
    res = pd.DataFrame(rows)
    print(res.to_string(index=False))
    print(f"\n평균 일치율: {res['일치율'].mean():.0%}  (같은 사람인데 판정이 바뀌면 촬영 조건이나 기준값 경계 문제)")
    print("\n특징값의 사람 내 흔들림(표준편차 평균):")
    print(res[[c for c in res.columns if c.endswith("표준편차")]].mean().round(3).to_string())
    if "length_ratio" in df.columns:
        print("\n→ 이 값들을 config.py 의 FACE_FEATURE_NOISE 에 넣으면 판정 확률(candidates)이 실제 흔들림을 반영합니다.")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("사용법: python consistency.py <features.csv> [--all]")
    main(sys.argv[1])
