"""휴대폰 측정 페이지(live/*.js)가 Python(face_shape.py · body_shape.py)과 같은 값을 내는지 확인.

같은 MediaPipe 결과(랜드마크·마스크)를 양쪽 측정 함수에 넣고 비교합니다. Node.js 가 필요합니다.
ai 폴더에서:  python tests/test_live_parity.py 사진또는영상 [사진또는영상 ...]
"""
import base64
import json
import os
import subprocess
import sys
import tempfile

import cv2
import mediapipe as mp
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import body_shape as B  # noqa: E402
import face_shape as F  # noqa: E402
from common import load_rgb  # noqa: E402


def frames(path, every=8, limit=12):
    if path.lower().endswith((".mp4", ".mov", ".avi")):
        cap, i, n = cv2.VideoCapture(path), 0, 0
        while n < limit:
            ok, fr = cap.read()
            if not ok:
                break
            if i % every == 0:
                yield f"{os.path.basename(path)}#{i}", cv2.cvtColor(fr, cv2.COLOR_BGR2RGB)
                n += 1
            i += 1
    else:
        yield os.path.basename(path), load_rgb(path)


def main(paths):
    cases, expect = [], {}
    for p in paths:
        for cid, rgb in frames(p):
            h, w = rgb.shape[:2]
            img = mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb))
            fr = F._get_landmarker().detect(img)
            if fr.face_landmarks:
                lm = fr.face_landmarks[0]
                f, _ = F.features_from_landmarks(lm, w, h)
                m = np.array(fr.facial_transformation_matrixes[0]).tolist() if fr.facial_transformation_matrixes else None
                cases.append({"id": "face:" + cid, "kind": "face", "w": w, "h": h, "matrix": m,
                              "lm": [{"x": q.x, "y": q.y, "z": q.z} for q in lm]})
                expect["face:" + cid] = {"f": f, "pose": F._head_pose(m) if m else None}
            br = B._get_landmarker().detect(img)
            issues, f, geo = B.measure(br, h, w, 175)
            cases.append({"id": "body:" + cid, "kind": "body", "w": w, "h": h,
                          "poses": [[{"x": q.x, "y": q.y, "z": q.z, "visibility": q.visibility} for q in lm]
                                    for lm in br.pose_landmarks],
                          "masks": [base64.b64encode(np.ascontiguousarray(m.numpy_view().squeeze(), np.float32).tobytes()).decode()
                                    for m in (br.segmentation_masks or [])]})
            exp = {"issues": issues, "f": None}
            if f:
                px_h = geo["foot_y"] - geo["top_y"]
                sh_y = (geo["P"][B.L_SH][1] + geo["P"][B.R_SH][1]) / 2
                exp["f"] = {"shoulder": f["shoulder_width_cm"] / 175, "waist": f["waist_width_cm"] / 175,
                            "hip": f["hip_width_cm"] / 175, "arm": f["arm_length_cm"] / 175,
                            "shoulder_height": (geo["foot_y"] - sh_y) / px_h,
                            "crotch_height": None if f["crotch_height_cm"] is None else f["crotch_height_cm"] / 175}
            expect["body:" + cid] = exp

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as fp:
        json.dump(cases, fp)
        tmp = fp.name
    try:
        res = subprocess.run(["node", os.path.join(HERE, "parity.mjs"), tmp], capture_output=True, text=True, check=True)
    finally:
        os.unlink(tmp)
    got = {r["id"]: r for r in json.loads(res.stdout)}

    worst, n_face, n_body, fails = {}, 0, 0, []
    for cid, e in expect.items():
        g = got[cid]
        if cid.startswith("face:"):
            n_face += 1
            for k, v in e["f"].items():
                d = abs(v - g["f"][k])
                worst[k] = max(worst.get(k, 0), d)
            if e["pose"]:
                d = max(abs(a - b) for a, b in zip(e["pose"], g["pose"]))
                worst["head_pose_deg"] = max(worst.get("head_pose_deg", 0), d)
        else:
            n_body += 1
            if sorted(e["issues"]) != sorted(g["issues"]):
                fails.append(f"{cid}: 문제 코드 다름 py={e['issues']} js={g['issues']}")
            if (e["f"] is None) != (g["f"] is None):
                fails.append(f"{cid}: 측정 성공 여부 다름")
            elif e["f"]:
                for k, v in e["f"].items():
                    if v is None or g["f"][k] is None:
                        if (v is None) != (g["f"][k] is None):
                            fails.append(f"{cid}: {k} 하나만 None")
                        continue
                    worst["body." + k] = max(worst.get("body." + k, 0), abs(v - g["f"][k]))
    print(f"얼굴 {n_face}프레임, 체형 {n_body}프레임 비교")
    for k, v in sorted(worst.items()):
        print(f"  {k:24s} 최대 차이 {v:.6f}")
    for x in fails:
        print("  ✗", x)
    tol_ok = all(v < (0.05 if k == "head_pose_deg" else 0.01) for k, v in worst.items())
    print("결과:", "일치" if tol_ok and not fails else "차이 있음")
    return 0 if tol_ok and not fails else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
