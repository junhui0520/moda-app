"""
테스트 실행기

  # 사진 1장 분석 (결과 JSON 출력 + 결과 이미지 저장)
  python run_test.py face 내얼굴.jpg
  python run_test.py body 내전신.jpg --height 175

  # 같은 사람 사진 여러 장 → 종합 판정 (파일 여러 개 또는 폴더)
  python run_test.py face-multi 사진1.jpg 사진2.jpg 사진3.jpg
  python run_test.py face-multi photos/kim

  # 폴더 전체 분석 → CSV (보정용 데이터 만들기)
  python run_test.py face-batch photos/face  --labels labels_face.csv  -o face_features.csv
  python run_test.py body-batch photos/body  --labels labels_body.csv  -o body_features.csv
"""
import argparse
import csv
import json
import os
import sys

os.environ.setdefault("GLOG_minloglevel", "2")   # MediaPipe 내부 로그 줄이기
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

from common import save_bgr

IMG_EXT = (".jpg", ".jpeg", ".png", ".webp", ".bmp")  # 아이폰 HEIC 는 JPG 로 변환해서 사용


def _save_vis(path, vis, outdir):
    if vis is None:
        return None
    os.makedirs(outdir, exist_ok=True)
    name = os.path.splitext(os.path.basename(path))[0] + "_result.jpg"
    dst = os.path.join(outdir, name)
    save_bgr(dst, vis)
    return dst


def _read_labels(path):
    """labels CSV: file,label[,height_cm][,person_id]"""
    if not path:
        return {}
    with open(path, encoding="utf-8-sig") as f:
        return {row["file"]: row for row in csv.DictReader(f)}


def single(kind, path, height, outdir):
    if kind == "face":
        from face_shape import analyze_face
        res, vis = analyze_face(path)
    else:
        from body_shape import analyze_body
        if not height:
            sys.exit("체형 분석은 --height (키, cm) 가 필요합니다.")
        res, vis = analyze_body(path, height)
    res["result_image"] = _save_vis(path, vis, outdir)
    print(json.dumps(res, ensure_ascii=False, indent=2))


def batch(kind, folder, labels_path, out_csv, default_height, outdir):
    labels = _read_labels(labels_path)
    files = sorted(f for f in os.listdir(folder) if f.lower().endswith(IMG_EXT))
    if not files:
        sys.exit(f"{folder} 에 이미지가 없습니다.")
    if kind == "face":
        from face_shape import analyze_face as fn
    else:
        from body_shape import analyze_body as fn

    rows, fails = [], 0
    for name in files:
        lab = labels.get(name, {})
        path = os.path.join(folder, name)
        try:
            if kind == "face":
                res, vis = fn(path)
            else:
                hgt = float(lab.get("height_cm") or default_height or 0)
                if not hgt:
                    print(f"[건너뜀] {name}: 키 정보 없음 (labels 의 height_cm 또는 --height)")
                    continue
                res, vis = fn(path, hgt)
        except Exception as e:  # 한 장이 실패해도 나머지는 계속
            res, vis = {"ok": False, "error": repr(e), "warnings": []}, None
        _save_vis(path, vis, outdir)
        status = res.get("face_shape") or res.get("body_shape") or ("실패: " + res.get("error", ""))
        if res.get("candidates"):
            status = ", ".join(f"{c['face_shape']} {c['prob']:.0%}" for c in res["candidates"])
        if res.get("usable") is False:
            status += "  [재촬영 권장]"
        warn = f"  ⚠ {' / '.join(res['warnings'])}" if res.get("warnings") else ""
        print(f"{name}: {status}{warn}")
        if not res.get("ok"):
            fails += 1
            continue
        row = {"file": name, **res["features"],
               "predicted": res.get("face_shape_key") or res.get("body_shape_key"),
               "usable": res.get("usable", True),
               "confidence": res.get("confidence", ""),
               "second": (res["candidates"][1]["key"] if len(res.get("candidates", [])) > 1 else ""),
               "label": lab.get("label", ""), "person_id": lab.get("person_id", ""),
               "warnings": " / ".join(res["warnings"])}
        rows.append(row)

    if rows:
        with open(out_csv, "w", newline="", encoding="utf-8-sig") as f:
            wr = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
            wr.writeheader()
            wr.writerows(rows)
    print(f"\n완료: 성공 {len(rows)}장, 실패 {fails}장 → {out_csv} / 결과 이미지: {outdir}/")


def multi(paths, outdir):
    from face_shape import analyze_face, analyze_faces_combined
    files = []
    for p in paths:
        if os.path.isdir(p):
            files += [os.path.join(p, f) for f in sorted(os.listdir(p)) if f.lower().endswith(IMG_EXT)]
        else:
            files.append(p)
    if not files:
        sys.exit("사진이 없습니다.")
    combined, per = analyze_faces_combined(files)
    print("── 사진별 결과 ──")
    for r in per:
        name = os.path.basename(r["file"])
        if not r.get("ok"):
            print(f"{name}: 실패 — {r.get('error')}")
            continue
        cands = ", ".join(f"{c['face_shape']} {c['prob']:.0%}" for c in r["candidates"])
        flag = "" if r["usable"] else "  [제외: " + " / ".join(r["warnings"]) + "]"
        print(f"{name}: {cands}{flag}")
    print("\n── 종합 판정 (품질 통과한 사진들의 측정값 중앙값) ──")
    print(json.dumps(combined, ensure_ascii=False, indent=2))


def main():
    ap = argparse.ArgumentParser(description="얼굴형·체형 무료 테스트")
    ap.add_argument("mode", choices=["face", "body", "face-multi", "face-batch", "body-batch"])
    ap.add_argument("path", nargs="+", help="이미지 파일 또는 폴더 (face-multi 는 여러 개 가능)")
    ap.add_argument("--height", type=float, help="키(cm). 체형 분석에 필요")
    ap.add_argument("--labels", help="정답 라벨 CSV (batch 모드, 선택)")
    ap.add_argument("-o", "--out", default=None, help="CSV 저장 경로 (batch 모드)")
    ap.add_argument("--vis-dir", default="results", help="결과 이미지 저장 폴더")
    a = ap.parse_args()

    if a.mode == "face-multi":
        multi(a.path, a.vis_dir)
    elif a.mode in ("face", "body"):
        single(a.mode, a.path[0], a.height, a.vis_dir)
    else:
        kind = a.mode.split("-")[0]
        batch(kind, a.path[0], a.labels, a.out or f"{kind}_features.csv", a.height, a.vis_dir)


if __name__ == "__main__":
    main()
