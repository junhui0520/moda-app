// 얼굴 측정 (face_shape.py · run_live.py 의 측정 부분을 그대로 옮긴 것)
// 브라우저·Node 어디서나 돌아가는 순수 함수만 둠 → tests/test_live_parity.py 가 Python 결과와 비교함.
// 기준값을 바꾸면 config.py 와 같이 바꿔야 합니다.

export const FACE_CFG = {
  T_FOREHEAD: 0.18, T_JAW: 0.80, T_CHIN: 0.92,          // config.FACE_T_*
  MAX_YAW_DEG: 10, MAX_PITCH_DEG: 15,                   // config.FACE_MAX_*
  MAX_JAW_OPEN: 0.15, MAX_SMILE: 0.35,
  MIN_FACE_FRAC: 0.18, MAX_FACE_FRAC: 0.50,             // config.LIVE_*
  MAX_CENTER_OFFSET: 0.20, MIN_BRIGHTNESS: 70, MAX_MOTION: 0.02,
  REPOSITION_YAW_DEG: 15,
};

// MediaPipe 얼굴 메시의 윤곽선 점 번호 (face_shape.FACE_OVAL)
export const FACE_OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379,
  378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
  162, 21, 54, 103, 67, 109];
const TOP = 10, CHIN = 152, EYE_A = 33, EYE_B = 263, CHEEK_A = 234, CHEEK_B = 454, NOSE_TIP = 1;
export const FEATURE_KEYS = ["length_ratio", "forehead_ratio", "jaw_ratio", "chin_ratio", "jaw_angle_deg"];

// run_live.MSG 와 같은 순서 (앞일수록 먼저 안내)
export const FACE_MSG = {
  no_face: "얼굴이 보이지 않아요",
  multi: "한 명만 화면에 나와 주세요",
  far: "조금 더 가까이 와 주세요",
  close: "조금 뒤로 가 주세요",
  center: "얼굴을 화면 가운데에 맞춰 주세요",
  dark: "더 밝은 곳에서 해 주세요",
  yaw: "정면을 봐 주세요",
  pitch_up: "턱을 조금 내려 주세요",
  pitch_down: "턱을 조금 들어 주세요",
  jaw: "입을 다물어 주세요",
  smile: "무표정으로 있어 주세요",
  moving: "잠시 움직이지 말아 주세요",
};
const ORDER = Object.keys(FACE_MSG);

const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const norm = (a) => Math.hypot(a[0], a[1], a[2]);
const scale = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];

// common.angle_at — 점 p 에서 a, b 를 바라보는 각도(도)
function angleAt(p, a, b) {
  const v1 = [a[0] - p[0], a[1] - p[1]], v2 = [b[0] - p[0], b[1] - p[1]];
  const c = (v1[0] * v2[0] + v1[1] * v2[1]) / (Math.hypot(...v1) * Math.hypot(...v2) + 1e-9);
  return Math.acos(Math.min(1, Math.max(-1, c))) * 180 / Math.PI;
}

// face_shape._width_at — 윤곽선을 수평선 y 로 잘랐을 때 (왼쪽 x, 오른쪽 x)
function widthAt(poly, y) {
  const xs = [];
  for (let i = 0; i < poly.length; i++) {
    const [x1, y1] = poly[i], [x2, y2] = poly[(i + 1) % poly.length];
    if ((y1 - y) * (y2 - y) <= 0 && y1 !== y2) xs.push(x1 + (y - y1) * (x2 - x1) / (y2 - y1));
  }
  if (xs.length < 2) return null;
  return [Math.min(...xs), Math.max(...xs)];
}

// face_shape._frontalize — 3D 랜드마크를 얼굴 기준 좌표계로 돌려 정면 2D 좌표로
function frontalize(p3) {
  let ex = [0, 0, 0];
  const c = sub(p3[CHEEK_B], p3[CHEEK_A]), e = sub(p3[EYE_B], p3[EYE_A]);
  ex = [c[0] + e[0], c[1] + e[1], c[2] + e[2]];
  ex = scale(ex, 1 / norm(ex));
  let ey = sub(p3[CHIN], p3[TOP]);
  ey = sub(ey, scale(ex, dot(ey, ex)));
  ey = scale(ey, 1 / norm(ey));
  const mean = [0, 0, 0];
  for (const q of p3) { mean[0] += q[0]; mean[1] += q[1]; mean[2] += q[2]; }
  mean[0] /= p3.length; mean[1] /= p3.length; mean[2] /= p3.length;
  return p3.map((q) => { const d = sub(q, mean); return [dot(d, ex), dot(d, ey)]; });
}

// face_shape._features_from_2d
function featuresFrom2d(p) {
  const poly = FACE_OVAL.map((i) => p[i]);
  const topY = p[TOP][1], chinY = p[CHIN][1], H = chinY - topY;
  if (H <= 0) return null;
  const row = (t) => widthAt(poly, topY + t * H);
  const cheekT = ((p[CHEEK_A][1] + p[CHEEK_B][1]) / 2 - topY) / H;
  const fore = row(FACE_CFG.T_FOREHEAD), cheek = row(cheekT), jaw = row(FACE_CFG.T_JAW), chin = row(FACE_CFG.T_CHIN);
  if (!fore || !cheek || !jaw || !chin) return null;
  const cheekW = cheek[1] - cheek[0];
  const yj = topY + FACE_CFG.T_JAW * H, yc = topY + cheekT * H;
  const left = angleAt([jaw[0], yj], [cheek[0], yc], p[CHIN]);
  const right = angleAt([jaw[1], yj], [cheek[1], yc], p[CHIN]);
  return {
    length_ratio: H / cheekW,
    forehead_ratio: (fore[1] - fore[0]) / cheekW,
    jaw_ratio: (jaw[1] - jaw[0]) / cheekW,
    chin_ratio: (chin[1] - chin[0]) / (jaw[1] - jaw[0]),
    jaw_angle_deg: (left + right) / 2,
  };
}

// face_shape.features_from_landmarks — lm: [{x,y,z}] (0~1 정규화), w·h: 프레임 크기(px)
export function featuresFromLandmarks(lm, w, h) {
  const p3 = lm.map((q) => [q.x * w, q.y * h, q.z * w]);
  return featuresFrom2d(frontalize(p3));
}

// face_shape._head_pose — 4x4 행렬(행 우선 2차원 배열) → [yaw, pitch, roll]
export function headPose(m) {
  const yaw = Math.atan2(-m[2][0], Math.hypot(m[0][0], m[1][0])) * 180 / Math.PI;
  const pitch = Math.atan2(m[2][1], m[2][2]) * 180 / Math.PI;
  const roll = Math.atan2(m[1][0], m[0][0]) * 180 / Math.PI;
  return [yaw, pitch, roll];
}

// MediaPipe JS 의 Matrix {rows, columns, data} → 행 우선 2차원 배열.
// JS 결과의 data 는 열 우선(column-major)으로 들어 있음 (tests/test_live_parity.py 로 확인).
export function matrixRows(mat) {
  const r = [];
  for (let i = 0; i < mat.rows; i++) {
    r.push([]);
    for (let j = 0; j < mat.columns; j++) r[i].push(mat.data[j * mat.rows + i]);
  }
  return r;
}

/**
 * 한 프레임 평가 (run_live.assess).
 * @param res     FaceLandmarker 결과 {faceLandmarks, faceBlendshapes, facialTransformationMatrixes}
 * @param w,h     프레임 크기
 * @param brightnessFn (x0,y0,x1,y1) → 그 영역 평균 밝기(0~255). 없으면 밝기 검사 생략
 * @param prevNose 직전 프레임 코끝 [x,y] 또는 null
 * @returns {issues, f, info, nose}
 */
export function assessFace(res, w, h, brightnessFn, prevNose) {
  const info = {};
  if (!res.faceLandmarks || !res.faceLandmarks.length) return { issues: ["no_face"], f: null, info, nose: null };
  const issues = [];
  if (res.faceLandmarks.length > 1) issues.push("multi");
  const lm = res.faceLandmarks[0];
  let xmin = Infinity, xmax = -Infinity, ymin = Infinity, ymax = -Infinity, sx = 0, sy = 0;
  for (const q of lm) {
    const x = q.x * w, y = q.y * h;
    xmin = Math.min(xmin, x); xmax = Math.max(xmax, x); ymin = Math.min(ymin, y); ymax = Math.max(ymax, y);
    sx += x; sy += y;
  }
  info.oval = FACE_OVAL.map((i) => [lm[i].x * w, lm[i].y * h]);
  const faceW = xmax - xmin, frac = faceW / w;
  info.faceFrac = frac;
  if (frac < FACE_CFG.MIN_FACE_FRAC) issues.push("far");
  else if (frac > FACE_CFG.MAX_FACE_FRAC) issues.push("close");
  const cx = sx / lm.length / w, cy = sy / lm.length / h;
  if (Math.abs(cx - 0.5) > FACE_CFG.MAX_CENTER_OFFSET || Math.abs(cy - 0.5) > FACE_CFG.MAX_CENTER_OFFSET + 0.05) issues.push("center");
  if (brightnessFn) {
    const x0 = Math.max(0, Math.floor(xmin)), x1 = Math.min(w, Math.floor(xmax));
    const y0 = Math.max(0, Math.floor(ymin)), y1 = Math.min(h, Math.floor(ymax));
    if (x1 > x0 && y1 > y0) {
      info.brightness = brightnessFn(x0, y0, x1, y1);
      if (info.brightness < FACE_CFG.MIN_BRIGHTNESS) issues.push("dark");
    }
  }
  const mats = res.facialTransformationMatrixes;
  if (mats && mats.length) {
    const [yaw, pitch] = headPose(matrixRows(mats[0]));
    info.yaw = yaw; info.pitch = pitch;
    if (Math.abs(yaw) > FACE_CFG.MAX_YAW_DEG) issues.push("yaw");
    if (pitch > FACE_CFG.MAX_PITCH_DEG) issues.push("pitch_up");
    else if (pitch < -FACE_CFG.MAX_PITCH_DEG) issues.push("pitch_down");
  }
  const bs = res.faceBlendshapes && res.faceBlendshapes[0];
  if (bs) {
    const s = {};
    for (const c of bs.categories) s[c.categoryName] = c.score;
    info.jaw_open = s.jawOpen || 0;
    info.smile = ((s.mouthSmileLeft || 0) + (s.mouthSmileRight || 0)) / 2;
    if (info.jaw_open > FACE_CFG.MAX_JAW_OPEN) issues.push("jaw");
    if (info.smile > FACE_CFG.MAX_SMILE) issues.push("smile");
  }
  const nose = [lm[NOSE_TIP].x * w, lm[NOSE_TIP].y * h];
  if (prevNose && Math.hypot(nose[0] - prevNose[0], nose[1] - prevNose[1]) / faceW > FACE_CFG.MAX_MOTION) issues.push("moving");
  const f = featuresFromLandmarks(lm, w, h);
  issues.sort((a, b) => ORDER.indexOf(a) - ORDER.indexOf(b));
  return { issues, f, info, nose };
}
