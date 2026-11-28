// tests/test_live_parity.py 가 부르는 Node 스크립트: 고정된 MediaPipe 결과를 live/*.js 측정 함수에 넣고 결과를 출력
import { readFileSync } from "node:fs";
import { featuresFromLandmarks, headPose } from "../live/measure_face.js";
import { measureBody } from "../live/measure_body.js";

const cases = JSON.parse(readFileSync(process.argv[2], "utf8"));
const out = [];
for (const c of cases) {
  if (c.kind === "face") {
    out.push({ id: c.id, f: featuresFromLandmarks(c.lm, c.w, c.h), pose: c.matrix ? headPose(c.matrix) : null });
  } else {
    const masks = c.masks.map((b64) => new Float32Array(Buffer.from(b64, "base64").buffer.slice(0)));
    const r = measureBody(c.poses, (i) => masks[i] || null, c.w, c.h);
    out.push({ id: c.id, issues: r.issues, f: r.f });
  }
}
console.log(JSON.stringify(out));
