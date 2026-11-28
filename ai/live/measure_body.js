// 체형 측정 (body_shape.measure · run_live_body.assess 를 그대로 옮긴 것)
// 순수 함수만 둠 → tests/test_live_parity.py 가 Python 결과와 비교함.
// 휴대폰은 '키 대비 비율'만 재서 보내고, cm 환산·Size Korea 비교·유형 판정은 AI 서버(Python)가 함.

export const BODY_CFG = {
  MIN_VISIBILITY: 0.6, MAX_SHOULDER_TILT_DEG: 6, EDGE_MARGIN: 0.0,     // config.BODY_*
  WAIST_SEARCH: [0.45, 0.90], HIP_SEARCH: [0.85, 1.15],
  LIVE_EDGE_MARGIN: 0.02, LIVE_MIN_HEIGHT_FRAC: 0.60,                  // config.LIVE_BODY_*
  LIVE_MAX_CENTER_OFFSET: 0.15, LIVE_MAX_TILT_DEG: 8, LIVE_MAX_MOTION: 0.03,
  REQUIRE_A_POSE: true,
  PLAUSIBLE: { shoulder_dev: [-0.30, 0.50], trunk_dev: [-0.30, 0.70] }, // config.BODY_PLAUSIBLE_DEV
};
// Size Korea 20대 남성 평균 (config.SIZE_KOREA_REF_MM) — 사람 몸으로 볼 수 있는 값인지 확인용
const SK = { height: 1732, bideltoid_width: 470, waist_width: 271, hip_width: 330 };

const L_SH = 11, R_SH = 12, L_EL = 13, R_EL = 14, L_WR = 15, R_WR = 16, L_IDX = 19, R_IDX = 20;
const L_HIP = 23, R_HIP = 24, L_KNEE = 25, R_KNEE = 26, L_ANK = 27, R_ANK = 28, L_HEEL = 29, R_HEEL = 30;
const REQUIRED = [L_SH, R_SH, L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANK, R_ANK];

// 우선순위 (run_live_body.PRIORITY) 와 실시간 안내 문구 (body_shape.ISSUES 의 짧은 문구 + LIVE_ONLY)
export const BODY_PRIORITY = ["no_person", "multi", "body_hidden", "feet_hidden", "head_cut", "feet_cut", "far", "center",
  "not_standing", "side", "tilt", "arms", "no_mask", "width_fail", "implausible", "moving", "legs"];
export const BODY_MSG = {
  no_person: "전신이 보이도록 서 주세요",
  multi: "한 명만 화면에 나와 주세요",
  feet_hidden: "발끝까지 보이게 뒤로 가 주세요",
  body_hidden: "몸 전체가 화면에 들어오게 서 주세요",
  not_standing: "똑바로 서 주세요",
  no_mask: "배경이 단순한 곳에 서 주세요",
  width_fail: "카메라 정면으로 서 주세요",
  implausible: "팔은 옆으로 벌리지 말고 살짝만 떼 주세요",
  tilt: "몸을 똑바로 세워 주세요",
  side: "카메라 정면으로 서 주세요",
  head_cut: "머리 위가 잘렸어요 — 조금 뒤로 가 주세요",
  feet_cut: "발 밑에 여백이 필요해요 — 조금 뒤로 가 주세요",
  arms: "팔을 몸에서 살짝 떼 주세요 (A자 자세)",
  legs: "다리를 살짝 벌리면 더 정확해요",
  far: "조금 더 가까이 와 주세요",
  center: "화면 가운데에 서 주세요",
  moving: "잠시 움직이지 말아 주세요",
};

/** run_live_body.blocking — 이 문제가 있으면 측정하지 않음 */
export function blocking(code) {
  if (code === "legs") return false;
  if (code === "arms") return BODY_CFG.REQUIRE_A_POSE;
  return true;
}

const clip = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

// body_shape._run_at — 마스크 한 줄에서 x=cx 를 포함하는 연속 구간
function runAt(mask, w, y, cx) {
  cx = Math.trunc(clip(cx, 0, w - 1));
  const o = y * w;
  if (!mask[o + cx]) return null;
  let l = cx, r = cx;
  while (l > 0 && mask[o + l - 1]) l--;
  while (r < w - 1 && mask[o + r + 1]) r++;
  return [l, r];
}

// cv2.connectedComponents(8방향) 후 (sx, sy) 가 속한 덩어리만 남김
function componentAt(bin, w, h, sx, sy) {
  const out = new Uint8Array(w * h);
  const start = sy * w + sx;
  if (!bin[start]) return null;
  const stack = [start];
  out[start] = 1;
  while (stack.length) {
    const p = stack.pop();
    const x = p % w, y = (p - x) / w;
    for (let dy = -1; dy <= 1; dy++) {
      const yy = y + dy;
      if (yy < 0 || yy >= h) continue;
      for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx;
        if (xx < 0 || xx >= w || (dx === 0 && dy === 0)) continue;
        const q = yy * w + xx;
        if (bin[q] && !out[q]) { out[q] = 1; stack.push(q); }
      }
    }
  }
  return out;
}

// cv2.polylines(두께 t) 근사: 선분에서 t/2 + 0.6 이내 픽셀을 칠함
// (+0.6 은 OpenCV 가 그린 선과 픽셀이 가장 많이 겹치는 값 — 실험으로 맞춤, 불일치 약 2.6%)
function strokeSegments(dst, w, h, pts, thick) {
  const r = thick / 2 + 0.6;
  for (let i = 0; i + 1 < pts.length; i++) {
    const [ax, ay] = pts[i], [bx, by] = pts[i + 1];
    const x0 = Math.max(0, Math.floor(Math.min(ax, bx) - r)), x1 = Math.min(w - 1, Math.ceil(Math.max(ax, bx) + r));
    const y0 = Math.max(0, Math.floor(Math.min(ay, by) - r)), y1 = Math.min(h - 1, Math.ceil(Math.max(ay, by) + r));
    const vx = bx - ax, vy = by - ay, L2 = vx * vx + vy * vy || 1;
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        let t = ((x - ax) * vx + (y - ay) * vy) / L2;
        t = clip(t, 0, 1);
        const dx = x - (ax + t * vx), dy = y - (ay + t * vy);
        if (dx * dx + dy * dy <= r * r) dst[y * w + x] = 1;
      }
    }
  }
}

function linspace(a, b, n) {
  const out = [];
  for (let i = 0; i < n; i++) out.push(a + (b - a) * i / (n - 1));
  return out;
}

/**
 * 한 프레임 측정 (body_shape.measure 의 사진 공통 부분).
 * @param poses  PoseLandmarker 결과 landmarks: [[{x,y,z,visibility}] ...]
 * @param maskOf (idx) → Float32Array(w*h) 사람 영역 확률 (0~1). 없으면 null
 * @param w,h    프레임 크기 (마스크 크기와 같아야 함)
 * @returns {issues, f, geo}  f = 키(머리끝~발끝 픽셀) 대비 비율 — AI 서버로 보내는 값
 */
export function measureBody(poses, maskOf, w, h) {
  const issues = [], geo = {};
  if (!poses || !poses.length) return { issues: ["no_person"], f: null, geo };

  const span = (lm) => Math.max(lm[L_ANK].y, lm[R_ANK].y) - Math.min(lm[L_SH].y, lm[R_SH].y);
  let idx = 0;
  for (let i = 1; i < poses.length; i++) if (span(poses[i]) > span(poses[idx])) idx = i;
  if (poses.length > 1) issues.push("multi");
  const lms = poses[idx];
  const P = lms.map((q) => [q.x * w, q.y * h]);
  const vis = lms.map((q) => q.visibility || 0);
  geo.P = P;

  const low = REQUIRED.filter((i) => vis[i] < BODY_CFG.MIN_VISIBILITY || !(lms[i].x >= 0 && lms[i].x <= 1 && lms[i].y >= 0 && lms[i].y <= 1));
  if (low.length) {
    issues.push(low.every((i) => [L_KNEE, R_KNEE, L_ANK, R_ANK].includes(i)) ? "feet_hidden" : "body_hidden");
    return { issues, f: null, geo };
  }
  let tilt = Math.atan2(P[L_SH][1] - P[R_SH][1], P[L_SH][0] - P[R_SH][0]) * 180 / Math.PI;
  tilt = Math.abs((((tilt + 90) % 180) + 180) % 180 - 90);   // Python 의 % 는 항상 0 이상
  geo.tilt = tilt;
  if (tilt > BODY_CFG.MAX_SHOULDER_TILT_DEG) issues.push("tilt");
  const shWJoint = Math.abs(P[L_SH][0] - P[R_SH][0]);
  const torsoLen = Math.abs((P[L_HIP][1] + P[R_HIP][1]) / 2 - (P[L_SH][1] + P[R_SH][1]) / 2);
  if (shWJoint < 0.35 * torsoLen) issues.push("side");
  const legLen = (P[L_ANK][1] + P[R_ANK][1]) / 2 - (P[L_HIP][1] + P[R_HIP][1]) / 2;
  if (legLen < 1.2 * torsoLen) { issues.push("not_standing"); return { issues, f: null, geo }; }

  const prob = maskOf ? maskOf(idx) : null;
  if (!prob) { issues.push("no_mask"); return { issues, f: null, geo }; }
  const bin = new Uint8Array(w * h);
  for (let i = 0; i < w * h; i++) bin[i] = prob[i] > 0.5 ? 1 : 0;
  const hipC = [(P[L_HIP][0] + P[R_HIP][0]) / 2, (P[L_HIP][1] + P[R_HIP][1]) / 2];
  const mask = componentAt(bin, w, h, Math.trunc(clip(hipC[0], 0, w - 1)), Math.trunc(clip(hipC[1], 0, h - 1)));
  if (!mask) { issues.push("no_mask"); return { issues, f: null, geo }; }

  let topY = -1, maxY = -1;
  for (let y = 0; y < h; y++) {
    const o = y * w;
    for (let x = 0; x < w; x++) if (mask[o + x]) { if (topY < 0) topY = y; maxY = y; break; }
  }
  const footY = Math.max(P[L_HEEL][1], P[R_HEEL][1], maxY);
  geo.top_y = topY; geo.foot_y = footY;
  if (topY <= Math.max(2, BODY_CFG.EDGE_MARGIN * h)) issues.push("head_cut");
  if (footY >= h - Math.max(3, BODY_CFG.EDGE_MARGIN * h)) issues.push("feet_cut");
  const pxHeight = footY - topY;

  const shY = (P[L_SH][1] + P[R_SH][1]) / 2, hipY = hipC[1], torso = hipY - shY;
  // 팔 영역을 지운 몸통 마스크 (어깨선 바로 아래까지는 지우지 않음)
  const arm = new Uint8Array(w * h);
  const thick = Math.max(3, Math.trunc(0.30 * shWJoint));
  for (const chain of [[L_SH, L_EL, L_WR, L_IDX], [R_SH, R_EL, R_WR, R_IDX]]) {
    strokeSegments(arm, w, h, chain.map((i) => [Math.trunc(P[i][0]), Math.trunc(P[i][1])]), thick);
  }
  const keepRows = Math.trunc(shY + 0.15 * torso);
  const torsoMask = mask.slice();
  for (let i = Math.max(0, keepRows) * w; i < w * h; i++) if (arm[i]) torsoMask[i] = 0;

  const centerX = (y) => { const t = (y - shY) / torso; return (P[L_SH][0] + P[R_SH][0]) / 2 * (1 - t) + hipC[0] * t; };
  const width = (y) => {
    const yi = Math.trunc(clip(y, 0, h - 1));
    const src = yi < shY + 0.15 * torso ? mask : torsoMask;
    const run = runAt(src, w, yi, centerX(yi));
    return run ? [run[1] - run[0] + 1, run, yi] : [null, null, yi];
  };
  const shoulder = width(shY + 0.04 * torso);
  let waist = null, hip = null;
  for (const t of linspace(...BODY_CFG.WAIST_SEARCH, 25)) {
    const r = width(shY + t * torso);
    if (!waist || (r[0] || 1e9) < (waist[0] || 1e9)) waist = r;
  }
  for (const t of linspace(...BODY_CFG.HIP_SEARCH, 25)) {
    const r = width(shY + t * torso);
    if (!hip || (r[0] || 0) > (hip[0] || 0)) hip = r;
  }
  if (!shoulder[0] || !waist[0] || !hip[0]) { issues.push("width_fail"); return { issues, f: null, geo }; }
  geo.lines = { shoulder, waist, hip };

  // 팔이 몸통에 붙어 있는지 (손목 위치로 확인)
  const fullRun = runAt(mask, w, Math.trunc(clip(hip[2], 0, h - 1)), centerX(hip[2]));
  for (const wr of [L_WR, R_WR]) {
    if (fullRun && vis[wr] > 0.5 && fullRun[0] - 5 <= P[wr][0] && P[wr][0] <= fullRun[1] + 5 &&
        shY + 0.6 * torso <= P[wr][1] && P[wr][1] <= shY + 1.3 * torso) { issues.push("arms"); break; }
  }

  // 샅높이: 무릎 높이의 다리 사이 빈틈에서 위로 올라가다 몸 영역이 처음 나오는 곳
  const cxLeg = Math.trunc(clip((P[L_KNEE][0] + P[R_KNEE][0]) / 2, 0, w - 1));
  const kneeY = Math.trunc(clip(Math.min(P[L_KNEE][1], P[R_KNEE][1]), 0, h - 1));
  let crotchY = null;
  if (!mask[kneeY * w + cxLeg]) {
    for (let y = kneeY; y > Math.trunc(hipY); y--) if (mask[y * w + cxLeg]) { crotchY = y; break; }
  }
  if (crotchY !== null) {
    const r = (footY - crotchY) / pxHeight;
    if (!(r >= 0.36 && r <= 0.55)) crotchY = null;
  }
  if (crotchY === null) issues.push("legs");
  geo.crotch = [cxLeg, crotchY];

  const armLen = (s, e, wr) => Math.hypot(P[s][0] - P[e][0], P[s][1] - P[e][1]) + Math.hypot(P[e][0] - P[wr][0], P[e][1] - P[wr][1]);
  const armPx = (armLen(L_SH, L_EL, L_WR) + armLen(R_SH, R_EL, R_WR)) / 2;

  const f = {
    shoulder: shoulder[0] / pxHeight,       // 어깨 폭 / 키
    waist: waist[0] / pxHeight,
    hip: hip[0] / pxHeight,
    arm: armPx / pxHeight,
    shoulder_height: (footY - shY) / pxHeight,
    crotch_height: crotchY === null ? null : (footY - crotchY) / pxHeight,
  };
  // body_shape.plausible — 사람 몸으로 볼 수 없는 값이면 잘못 잰 프레임
  const dev = (frac, ref) => frac / (ref / SK.height) - 1;
  const shDev = dev(f.shoulder, SK.bideltoid_width);
  const trDev = (dev(f.waist, SK.waist_width) + dev(f.hip, SK.hip_width)) / 2;
  geo.shoulder_dev = shDev; geo.trunk_dev = trDev;
  const [sl, sh] = BODY_CFG.PLAUSIBLE.shoulder_dev, [tl, th] = BODY_CFG.PLAUSIBLE.trunk_dev;
  if (!(sl <= shDev && shDev <= sh && tl <= trDev && trDev <= th)) { issues.push("implausible"); return { issues, f: null, geo }; }
  return { issues, f, geo };
}

/** run_live_body.assess — 실시간 전용 검사(거리·가운데·움직임)를 더하고 우선순위로 정렬 */
export function assessBody(poses, maskOf, w, h, prevHip) {
  const { issues, f, geo } = measureBody(poses, maskOf, w, h);
  if (geo.top_y !== undefined) {
    const frac = (geo.foot_y - geo.top_y) / h;
    geo.height_frac = frac;
    if (frac < BODY_CFG.LIVE_MIN_HEIGHT_FRAC) issues.push("far");
    const m = BODY_CFG.LIVE_EDGE_MARGIN * h;
    if (geo.top_y <= m && !issues.includes("head_cut")) issues.push("head_cut");
    if (geo.foot_y >= h - m && !issues.includes("feet_cut")) issues.push("feet_cut");
  }
  let hip = null;
  const P = geo.P;
  if (P) {
    hip = [(P[L_HIP][0] + P[R_HIP][0]) / 2, (P[L_HIP][1] + P[R_HIP][1]) / 2];
    const shY = (P[L_SH][1] + P[R_SH][1]) / 2, torso = Math.max(1, hip[1] - shY);
    if (Math.abs(hip[0] / w - 0.5) > BODY_CFG.LIVE_MAX_CENTER_OFFSET) issues.push("center");
    if (prevHip && Math.hypot(hip[0] - prevHip[0], hip[1] - prevHip[1]) / torso > BODY_CFG.LIVE_MAX_MOTION) issues.push("moving");
  }
  let out = issues;
  if (out.includes("tilt") && (geo.tilt || 0) <= BODY_CFG.LIVE_MAX_TILT_DEG) out = out.filter((c) => c !== "tilt");
  out = [...new Set(out)].sort((a, b) => BODY_PRIORITY.indexOf(a) - BODY_PRIORITY.indexOf(b));
  return { issues: out, f, geo, hip };
}
