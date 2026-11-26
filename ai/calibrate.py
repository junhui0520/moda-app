"""
기준값 보정: 팀이 라벨을 붙인 사진으로 '임시 규칙'이 얼마나 맞는지 확인하고,
데이터에서 배운 판정 규칙(결정나무)과 정확도를 보여줍니다.

  python calibrate.py face_features.csv
  python calibrate.py body_features.csv

CSV 는 run_test.py 의 batch 모드 결과이며, label 열이 채워져 있어야 합니다.
label 값은 config.py 의 키(영문)나 한글 이름 둘 다 가능합니다.
  예) long / 긴형, square / 각진형 ...
person_id 열이 있으면 '같은 사람 사진이 학습·평가에 동시에 들어가지 않게' 나눠서 평가합니다.
"""
import sys

import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import GroupKFold, StratifiedKFold, cross_val_predict
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.tree import DecisionTreeClassifier, export_text

import config

FACE_FEATURES = ["length_ratio", "forehead_ratio", "jaw_ratio", "chin_ratio", "jaw_angle_deg"]
BODY_FEATURES = ["shoulder_dev", "trunk_dev", "taper_dev", "arm_dev"]  # limb_dev 는 촬영 높이에 민감해 제외


def main(path):
    df = pd.read_csv(path, encoding="utf-8-sig")
    is_face = "length_ratio" in df.columns
    feats = FACE_FEATURES if is_face else BODY_FEATURES
    labels = config.FACE_LABELS if is_face else config.BODY_LABELS
    ko2key = {v: k for k, v in labels.items()}

    df["label"] = df["label"].fillna("").astype(str).str.strip().map(lambda x: ko2key.get(x, x))
    df = df[df["label"] != ""]
    if "usable" in df.columns:
        n_bad = (~df["usable"].astype(bool)).sum()
        df = df[df["usable"].astype(bool)]
        if n_bad:
            print(f"촬영 품질 미달 {n_bad}장은 제외하고 평가합니다.")
    if len(df) < 10:
        sys.exit(f"라벨이 붙은 사진이 {len(df)}장뿐입니다. 최소 수십 장(유형별 5장 이상)은 있어야 의미가 있습니다.")

    print(f"라벨 데이터: {len(df)}장")
    print(df["label"].value_counts().to_string(), "\n")

    # 1) 지금 config.py 임시 규칙의 성적
    print("=" * 60)
    print("[1] 현재 임시 규칙(config.py) 성적")
    print("=" * 60)
    print(classification_report(df["label"], df["predicted"], zero_division=0))

    # 2) 데이터로 배운 모델 (교차검증)
    X, y = df[feats], df["label"]
    n_min = y.value_counts().min()
    k = max(2, min(5, n_min))
    if "person_id" in df.columns and df["person_id"].notna().any() and df["person_id"].astype(str).str.strip().ne("").any():
        groups = df["person_id"].astype(str)
        k = min(k, groups.nunique())
        cv, cv_kw = GroupKFold(n_splits=k), {"groups": groups}
        how = f"사람 단위 {k}-fold"
    else:
        cv, cv_kw = StratifiedKFold(n_splits=k, shuffle=True, random_state=0), {}
        how = f"{k}-fold (person_id 없음 — 같은 사람 사진이 여러 장이면 점수가 부풀 수 있음)"

    models = {
        "결정나무(깊이 3)": DecisionTreeClassifier(max_depth=3, min_samples_leaf=3, random_state=0),
        "로지스틱 회귀": make_pipeline(StandardScaler(), LogisticRegression(max_iter=2000)),
    }
    for name, m in models.items():
        print("=" * 60)
        print(f"[2] {name} — {how}")
        print("=" * 60)
        pred = cross_val_predict(m, X, y, cv=cv, **cv_kw)
        print(classification_report(y, pred, zero_division=0))
        order = sorted(y.unique())
        cm = pd.DataFrame(confusion_matrix(y, pred, labels=order), index=[f"정답:{o}" for o in order],
                          columns=[f"예측:{o}" for o in order])
        print(cm.to_string(), "\n")

    # 3) 전체 데이터로 학습한 결정나무 규칙 → config.py 기준값 고칠 때 참고
    tree = DecisionTreeClassifier(max_depth=3, min_samples_leaf=3, random_state=0).fit(X, y)
    print("=" * 60)
    print("[3] 데이터에서 찾은 판정 규칙 (config.py 기준값 수정 참고용)")
    print("=" * 60)
    print(export_text(tree, feature_names=feats, decimals=3))
    print("유형별 특징 평균:")
    print(df.groupby("label")[feats].mean().round(3).to_string())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("사용법: python calibrate.py <features.csv>")
    main(sys.argv[1])
