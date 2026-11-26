"""
지금 적용 중인 얼굴형·체형 판정 기준을 '실제 수치'로 출력합니다.

  python show_criteria.py

- face_population.json / body_population.json (fit_*.py --save 로 만든 팀 기준)이 있으면 그 값을,
  없으면 config.py 임시값을 기준으로 계산합니다.
- 얼굴형은 여러 특징을 합친 점수로 판정하므로, '다른 특징은 평균이고 이 특징 하나만 달라질 때
  몇부터 어떤 유형이 되는지'를 함께 보여줍니다.
"""
import math

import numpy as np

import body_shape as B
import config
import face_shape as F

KO = {"length_ratio": "얼굴 길이/광대 폭", "forehead_ratio": "이마 폭/광대 폭", "jaw_ratio": "턱 폭/광대 폭",
      "chin_ratio": "턱끝 폭/턱 폭", "jaw_angle_deg": "턱 각도(°)"}


def face():
    print("=" * 78)
    print("얼굴형")
    print("=" * 78)
    if config.FACE_CLASSIFIER == "rules":
        r = config.FACE_RULES
        print("판정 방식: 고정 규칙 (FACE_CLASSIFIER = 'rules') — 위에서부터 먼저 맞는 것")
        print(f"  1) 길이비 ≥ {r['long_min_length_ratio']} 이고 (턱비 ≥ {r['rect_min_jaw_ratio']} 또는 턱각 ≤ {r['rect_max_jaw_angle']}°) → 직사각형, 아니면 긴형")
        print(f"  2) 턱비 ≥ {r['rect_min_jaw_ratio']} 이고 턱각 ≤ {r['rect_max_jaw_angle']}° → 직사각형")
        print(f"  3) 이마비 ≤ {r['diamond_max_forehead_ratio']} 이고 턱비 ≤ {r['diamond_max_jaw_ratio']} → 다이아몬드형")
        print(f"  4) 이마비 − 턱비 ≥ {r['heart_min_forehead_minus_jaw']} 이고 턱끝비 ≤ {r['heart_max_chin_ratio']} → 하트형")
        print(f"  5) 길이비 ≤ {r['round_max_length_ratio']} → 둥근형")
        print("  6) 나머지 → 계란형")
        return
    pop = F.population()
    print(f"판정 방식: 평균 대비 (relative)   기준 출처: {pop['source']}")
    print(f"계란형 반경(FACE_OVAL_RADIUS): {config.FACE_OVAL_RADIUS}")
    print("\n[1] 평균 얼굴과 '1칸(z=1)'의 크기")
    print(f"  {'특징':18s} {'평균':>8s} {'1칸':>8s} {'신뢰도 가중치':>12s}")
    for k in F.FEATURE_KEYS:
        sp = max(pop["spread"][k], config.FACE_Z_MIN_SPREAD[k])
        print(f"  {KO[k]:18s} {pop['median'][k]:8.3f} {sp:8.3f} {pop['weights'].get(k, 1.0):12.2f}")

    print("\n[2] 유형별 방향 (가중치 반영 후, 합이 1이 되게 정규화) — 이 방향으로 많이 갈수록 그 유형 점수↑")
    w = pop["weights"]
    for t, protos in config.FACE_PROTOTYPES.items():
        for i, p in enumerate(protos if isinstance(protos, list) else [protos]):
            q = {k: v * w.get(k, 1.0) for k, v in p.items()}
            n = math.sqrt(sum(v * v for v in q.values()))
            s = ", ".join(f"{KO[k]} {'+' if v > 0 else '−'}{abs(v) / n:.2f}" for k, v in q.items())
            print(f"  {config.FACE_LABELS[t]:7s}{'(2)' if i else '   '} {s}")
    print(f"  계란형      위 점수가 모두 {config.FACE_OVAL_RADIUS} 미만일 때")
    print("\n  유형 점수 = Σ (특징의 z점수 × 위 방향 숫자)    → 가장 높은 유형, 단 최고 점수 < 반경이면 계란형")

    print("\n[3] 한 특징만 달라질 때 (나머지는 평균) 유형이 바뀌는 지점")
    base = dict(pop["median"])
    for k in F.FEATURE_KEYS:
        sp = max(pop["spread"][k], config.FACE_Z_MIN_SPREAD[k])
        for sign, word in ((+1, "커지면"), (-1, "작아지면")):
            hit = None
            for z in np.arange(0, 6.001, 0.01):
                f = dict(base)
                f[k] = base[k] + sign * z * sp
                t = F.classify(f)
                if t != "oval":
                    hit = (f[k], t, z)
                    break
            if hit:
                op = "≥" if sign > 0 else "≤"
                print(f"  {KO[k]:18s} {word:5s} {op} {hit[0]:7.3f}  → {config.FACE_LABELS[hit[1]]}  (평균에서 {hit[2]:.2f}칸)")
            else:
                print(f"  {KO[k]:18s} {word:5s} 이것만으로는 계란형 유지 (다른 특징과 같이 달라져야 함)")


def body():
    print("\n" + "=" * 78)
    print("체형")
    print("=" * 78)
    ref = config.SIZE_KOREA_REF_MM
    print("측정값 정의 (Size Korea 20대 남성 평균 대비 편차, 키 대비 비율 기준)")
    print(f"  어깨 편차 = (사진 어깨 폭/키) ÷ ({ref['bideltoid_width']}/{ref['height']}) − 1      ※ 위팔사이너비 기준")
    print(f"  몸통 편차 = (허리 편차 + 엉덩이 편차)/2,  허리 기준 {ref['waist_width']}mm, 엉덩이 기준 {ref['hip_width']}mm")
    print("  어깨−몸통 = 어깨 편차 − 몸통 편차 (몸통에 비해 어깨가 얼마나 넓은지)")

    relative = config.BODY_CLASSIFIER != "rules" and B.relative_ready()
    if not relative:
        r = config.BODY_RULES
        why = "(팀 기준 body_population.json 이 없어서)" if config.BODY_CLASSIFIER != "rules" else ""
        print(f"\n판정 방식: Size Korea 절대 기준 규칙 {why} — 위에서부터 먼저 맞는 것")
        print(f"  1) |어깨 편차| ≤ {r['standard_tol']:.0%} 이고 |몸통 편차| ≤ {r['standard_tol']:.0%}        → 표준체형")
        print(f"  2) 몸통 편차 ≥ +{r['large_rect_min_trunk']:.0%}                                  → 큰 사각체형")
        print(f"  3) 몸통 편차 ≤ {r['small_inv_max_trunk']:+.0%} 이고 어깨−몸통 ≥ +{r['small_inv_min_shoulder_gap']:.0%}       → 작은 역삼각체형")
        print("  4) 나머지                                              → 사각체형")
        return
    pop = B.body_population()
    r = pop["rules"]
    m, s = pop["median"], pop["spread"]
    print(f"\n판정 방식: 팀 평균 대비 (relative)   기준 출처: {pop['source']}")
    print(f"  팀 평균: 어깨 편차 {m['shoulder_dev']:+.1%}, 몸통 편차 {m['trunk_dev']:+.1%}, 어깨−몸통 {m['upper_taper']:+.1%}")
    print(f"  1칸 크기: 어깨 {s['shoulder_dev']:.1%}p, 몸통 {s['trunk_dev']:.1%}p, 어깨−몸통 {s['upper_taper']:.1%}p")
    print("  위에서부터 먼저 맞는 것:")
    R = r["standard_radius"]
    print(f"  1) √(어깨 z² + 몸통 z²) ≤ {R}                                 → 표준체형")
    print(f"       예) 몸통이 팀 평균일 때 어깨 편차 {m['shoulder_dev'] - R * s['shoulder_dev']:+.1%} ~ {m['shoulder_dev'] + R * s['shoulder_dev']:+.1%}"
          f" / 어깨가 팀 평균일 때 몸통 편차 {m['trunk_dev'] - R * s['trunk_dev']:+.1%} ~ {m['trunk_dev'] + R * s['trunk_dev']:+.1%}")
    T = r["large_rect_min_trunk_z"]
    print(f"  2) 몸통 z ≥ {T}   즉 몸통 편차 ≥ {m['trunk_dev'] + T * s['trunk_dev']:+.1%}                 → 큰 사각체형")
    U = r["small_inv_min_taper_z"]
    print(f"  3) 어깨−몸통 z ≥ {U}   즉 어깨 편차 − 몸통 편차 ≥ {m['upper_taper'] + U * s['upper_taper']:+.1%}    → 작은 역삼각체형")
    print("  4) 나머지                                                   → 사각체형")


if __name__ == "__main__":
    face()
    body()
