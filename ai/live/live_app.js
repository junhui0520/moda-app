// Moda 실시간 분석 페이지 — 휴대폰 앱의 WebView 가 여는 화면 (run_live.py · run_live_body.py 를 옮긴 것)
//
//   /live/live.html?mode=face                얼굴형
//   /live/live.html?mode=body                체형  (판정에 필요한 키는 백엔드가 붙이므로 여기서는 안 받음)
//   선택: &rounds=2  &camera=user|environment  &debug=1
//         &height=175 (앱 없이 PC 에서 테스트할 때만)  &apose=0 (팔을 내린 자세도 측정 — run_live_body.py --allow-arms-down)
//         &res=native (카메라 해상도를 지정하지 않음 — 테스트 영상을 가짜 카메라로 넣을 때)
//
// 측정이 끝나면 앱(WebView)에 메시지를 보냄 → 앱이 그대로 백엔드에 전달 → 백엔드가 AI 서버 /v1/live/{mode} 로 판정.
//   { type: "moda-live-result", mode, payload: { rounds: [[프레임 측정값...], ...], quality: {...} } }
//   { type: "moda-live-status", status: "loading|ready|measuring|between|done|error", code?, message? }
// 앱 밖(PC 브라우저)에서 열면 같은 서버의 /v1/live/{mode} 를 직접 불러 결과를 화면에 보여 줌 (테스트용).

import { assessFace, FACE_MSG, FACE_CFG } from "./measure_face.js";
import { assessBody, BODY_MSG, BODY_CFG, blocking } from "./measure_body.js";

const TASKS_VISION_VERSION = "1.2.3";   // Python mediapipe 와 같은 버전 (같은 모델·같은 결과)
const CDN = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${TASKS_VISION_VERSION}`;

const params = new URLSearchParams(location.search);
const MODE = params.get("mode") === "body" ? "body" : "face";
const ROUNDS = Math.min(3, Math.max(1, parseInt(params.get("rounds") || "2", 10) || 2));
const CAMERA = params.get("camera") === "environment" ? "environment" : "user";
const DEBUG = params.get("debug") === "1";
const TEST_HEIGHT = Number(params.get("height")) || 175;
if (params.get("apose") === "0") BODY_CFG.REQUIRE_A_POSE = false;

// config.py 의 LIVE_* 값과 같음
const FRAMES = MODE === "face" ? 6 : 5;              // LIVE_TARGET_FRAMES / LIVE_BODY_TARGET_FRAMES
const INTERVAL = 0.30;                                // LIVE_MIN_INTERVAL_SEC / LIVE_BODY_MIN_INTERVAL_SEC
const FACE_REPOSITION_TIMEOUT = 5;                    // LIVE_REPOSITION_TIMEOUT_SEC
const BODY_REPOSITION_TIMEOUT = 6;                    // LIVE_BODY_REPOSITION_TIMEOUT_SEC
const BODY_COUNTDOWN = 3, BODY_NEXT_COUNTDOWN = 1.5;  // LIVE_BODY_COUNTDOWN_SEC / NEXT_COUNTDOWN_SEC
const BODY_PROCESS_MAX_SIDE = 960;                    // LIVE_BODY_PROCESS_MAX_SIDE

const $ = (id) => document.getElementById(id);
const video = $("video"), overlay = $("overlay"), ctx = overlay.getContext("2d");
const embedded = () => !!window.ReactNativeWebView || window.parent !== window;

function post(msg) {
  if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(msg));
  else if (window.parent !== window) window.parent.postMessage(msg, "*");
}
function status(s, extra = {}) { post({ type: "moda-live-status", mode: MODE, status: s, ...extra }); }

function showPanel(title, text, { spin = false, button = null, json = null } = {}) {
  $("stage").hidden = true;
  $("panel").hidden = false;
  $("spin").hidden = !spin;
  $("ptitle").textContent = title;
  $("ptext").textContent = text || "";
  $("pjson").hidden = !json;
  if (json) $("pjson").textContent = json;
  $("pbtn").hidden = !button;
  if (button) { $("pbtn").textContent = button.label; $("pbtn").onclick = button.onClick; }
}

const median = (a) => { const s = [...a].sort((x, y) => x - y), m = s.length >> 1; return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; };

// ---------------------------------------------------------------------------
// MediaPipe 준비 (같은 서버의 vendor/ 를 먼저 쓰고, 없으면 CDN)
// ---------------------------------------------------------------------------
async function loadVision() {
  let mod, wasm;
  try {
    mod = await import("./vendor/vision_bundle.mjs");
    wasm = new URL("./vendor/wasm", location.href).href;
  } catch (e) {
    mod = await import(`${CDN}/vision_bundle.mjs`);
    wasm = `${CDN}/wasm`;
  }
  const fileset = await mod.FilesetResolver.forVisionTasks(wasm);
  const model = (name) => new URL(`./models/${name}`, location.href).href;
  if (MODE === "face") {
    return mod.FaceLandmarker.createFromOptions(fileset, {
      baseOptions: { modelAssetPath: model("face_landmarker.task"), delegate: "CPU" },
      runningMode: "VIDEO", numFaces: 2,
      outputFaceBlendshapes: true, outputFacialTransformationMatrixes: true,
    });
  }
  return mod.PoseLandmarker.createFromOptions(fileset, {
    baseOptions: { modelAssetPath: model("pose_landmarker_full.task"), delegate: "CPU" },
    runningMode: "VIDEO", numPoses: 2, outputSegmentationMasks: true,
  });
}

async function openCamera() {
  // 해상도를 지정하지 않으면 휴대폰 WebView 가 640x480 으로 여는 경우가 있어 1280x720 을 요청
  // (세로로 든 휴대폰에서는 720x1280 세로 영상이 들어옴)
  const size = params.get("res") === "native" ? {} : { width: { ideal: 1280 }, height: { ideal: 720 } };
  const stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: { facingMode: CAMERA, ...size } });
  video.srcObject = stream;
  await video.play();
  await new Promise((r) => (video.videoWidth ? r() : video.addEventListener("loadedmetadata", r, { once: true })));
  if (CAMERA === "user") { video.classList.add("mirror"); overlay.classList.add("mirror"); }
  return stream;
}

// ---------------------------------------------------------------------------
// 화면 표시
// ---------------------------------------------------------------------------
function setUi({ step, progress, msg, ok = false, sub = "", count = null }) {
  $("step").textContent = step;
  $("progress").firstElementChild.style.width = `${Math.round(progress * 100)}%`;
  $("msg").textContent = msg;
  $("msg").className = ok ? "ok" : "";
  $("sub").textContent = sub;
  $("count").style.display = count === null ? "none" : "block";
  if (count !== null) $("count").textContent = String(count);
}

// 영상(object-fit: cover) 좌표 → 화면 좌표
function fitOverlay(w, h) {
  const cw = overlay.clientWidth, ch = overlay.clientHeight, dpr = window.devicePixelRatio || 1;
  if (overlay.width !== Math.round(cw * dpr) || overlay.height !== Math.round(ch * dpr)) {
    overlay.width = Math.round(cw * dpr); overlay.height = Math.round(ch * dpr);
  }
  const s = Math.max(cw / w, ch / h);
  ctx.setTransform(dpr * s, 0, 0, dpr * s, dpr * (cw - w * s) / 2, dpr * (ch - h * s) / 2);
  ctx.clearRect(-1e4, -1e4, 3e4, 3e4);
  return s;
}

function drawFace(info, w, h, good) {
  const s = fitOverlay(w, h);
  ctx.lineWidth = 2.5 / s;
  if (info.oval) {
    ctx.strokeStyle = good ? "#22C55E" : "rgba(255,255,255,.85)";
    ctx.beginPath();
    info.oval.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
    ctx.closePath(); ctx.stroke();
  } else {   // 얼굴이 없을 때: 맞춰 설 자리 표시
    const rx = w * (FACE_CFG.MIN_FACE_FRAC + FACE_CFG.MAX_FACE_FRAC) / 4;
    ctx.setLineDash([10 / s, 10 / s]); ctx.strokeStyle = "rgba(255,255,255,.6)";
    ctx.beginPath(); ctx.ellipse(w / 2, h / 2, rx, rx * 1.35, 0, 0, Math.PI * 2); ctx.stroke(); ctx.setLineDash([]);
  }
}

function drawBody(geo, w, h, k, good) {
  const s = fitOverlay(w, h);
  ctx.lineWidth = 3 / s;
  if (geo.lines) {
    for (const [name, col] of [["shoulder", "#22C55E"], ["waist", "#60A5FA"], ["hip", "#F472B6"]]) {
      const [, run, y] = geo.lines[name];
      ctx.strokeStyle = col; ctx.beginPath(); ctx.moveTo(run[0] / k, y / k); ctx.lineTo(run[1] / k, y / k); ctx.stroke();
    }
  }
  if (geo.top_y !== undefined) {
    ctx.strokeStyle = good ? "#22C55E" : "rgba(255,255,255,.6)";
    ctx.setLineDash([10 / s, 10 / s]);
    for (const y of [geo.top_y, geo.foot_y]) { ctx.beginPath(); ctx.moveTo(w * 0.2, y / k); ctx.lineTo(w * 0.8, y / k); ctx.stroke(); }
    ctx.setLineDash([]);
  }
}

// ---------------------------------------------------------------------------
// 측정 흐름
// ---------------------------------------------------------------------------
let lastTs = 0;
const nextTs = () => { let t = performance.now(); if (t <= lastTs) t = lastTs + 1; lastTs = t; return t; };

function makeBrightness() {
  const c = document.createElement("canvas"), g = c.getContext("2d", { willReadFrequently: true });
  return (w, h) => {
    const k = 160 / w; c.width = 160; c.height = Math.max(1, Math.round(h * k));
    g.drawImage(video, 0, 0, c.width, c.height);
    const img = g.getImageData(0, 0, c.width, c.height).data;
    return (x0, y0, x1, y1) => {   // cv2 회색 변환과 같은 가중치
      const a = Math.floor(x0 * k), b = Math.max(a + 1, Math.floor(x1 * k)), cc = Math.floor(y0 * k), d = Math.max(cc + 1, Math.floor(y1 * k));
      let sum = 0, n = 0;
      for (let y = cc; y < Math.min(d, c.height); y++) for (let x = a; x < Math.min(b, c.width); x++) {
        const i = (y * c.width + x) * 4; sum += 0.299 * img[i] + 0.587 * img[i + 1] + 0.114 * img[i + 2]; n++;
      }
      return n ? sum / n : 255;
    };
  };
}

function faceFlow(landmarker) {
  const st = { state: "collect", collected: [], rounds: [], lastKeep: -1e9, prevNose: null, betweenStart: 0, seenAway: false, q: [] };
  const bright = makeBrightness();
  return () => {
    const w = video.videoWidth, h = video.videoHeight, ts = nextTs(), now = ts / 1000;
    const res = landmarker.detectForVideo(video, ts);
    const { issues, f, info, nose } = assessFace(res, w, h, bright(w, h), st.prevNose);
    st.prevNose = nose;
    const round = st.rounds.length + 1;
    if (st.state === "collect") {
      if (!issues.length && f && now - st.lastKeep >= INTERVAL) {
        st.collected.push(f); st.lastKeep = now;
        st.q.push({ yaw: info.yaw, pitch: info.pitch, face_frac: info.faceFrac, brightness: info.brightness });
      }
      if (st.collected.length >= FRAMES) {
        st.rounds.push(st.collected); st.collected = [];
        if (st.rounds.length < ROUNDS) { st.state = "between"; st.betweenStart = now; st.seenAway = false; status("between"); }
        else return finish(st.rounds, quality(st.q));
      }
    } else if (st.state === "between") {
      // 다음 회차 전에 고개를 한 번 돌렸다가 다시 정면 → 회차마다 조건이 달라져 오차가 상쇄됨
      const away = !res.faceLandmarks?.length || Math.abs(info.yaw || 0) > FACE_CFG.REPOSITION_YAW_DEG ||
        Math.abs(info.pitch || 0) > FACE_CFG.REPOSITION_YAW_DEG;
      if (away) st.seenAway = true;
      const timedOut = now - st.betweenStart > FACE_REPOSITION_TIMEOUT;
      if ((st.seenAway || timedOut) && !issues.length && f) { st.state = "collect"; st.lastKeep = now; status("measuring"); }
    }
    drawFace(info, w, h, !issues.length && st.state === "collect");
    const done = (st.rounds.length * FRAMES + st.collected.length) / (ROUNDS * FRAMES);
    if (st.state === "between") {
      setUi({ step: `얼굴형 분석 · ${st.rounds.length}/${ROUNDS}회 완료`, progress: done,
        msg: st.seenAway ? "좋아요, 다시 정면을 봐 주세요" : "고개를 옆으로 한 번 돌렸다가",
        sub: st.seenAway ? "" : "다시 정면을 봐 주세요 — 한 번 더 재서 정확도를 높여요" });
    } else {
      setUi({ step: `얼굴형 분석 · ${round}/${ROUNDS}회차`, progress: done,
        msg: issues.length ? FACE_MSG[issues[0]] : "좋아요, 그대로 있어 주세요", ok: !issues.length,
        sub: issues.length ? "" : "무표정으로 정면을 봐 주세요" });
    }
    if (DEBUG && f) $("sub").textContent = Object.entries(f).map(([k, v]) => `${k.split("_")[0]} ${v.toFixed(2)}`).join("  ");
  };
}

function bodyFlow(landmarker) {
  const st = { state: "setup", collected: [], rounds: [], lastKeep: -1e9, prevHip: null, tCount: 0, badSince: null,
    betweenStart: 0, seenAway: false, q: [] };
  const proc = document.createElement("canvas"), pg = proc.getContext("2d");
  return () => {
    const vw = video.videoWidth, vh = video.videoHeight;
    const k = Math.min(1, BODY_PROCESS_MAX_SIDE / Math.max(vw, vh));   // 분석은 긴 변 960px 로 줄여서
    proc.width = Math.round(vw * k); proc.height = Math.round(vh * k);
    pg.drawImage(video, 0, 0, proc.width, proc.height);
    const ts = nextTs(), now = ts / 1000;
    let out = null;
    landmarker.detectForVideo(proc, ts, (res) => {   // 마스크는 이 콜백 안에서만 유효
      const masks = res.segmentationMasks || [];
      out = assessBody(res.landmarks, (i) => (masks[i] ? masks[i].getAsFloat32Array() : null), proc.width, proc.height, st.prevHip);
    });
    const { issues, f, geo, hip } = out;
    st.prevHip = hip;
    const blk = issues.filter(blocking), good = !blk.length && !!f;
    const cd = st.rounds.length ? BODY_NEXT_COUNTDOWN : BODY_COUNTDOWN;

    if (st.state === "setup") {
      if (good) { st.state = "countdown"; st.tCount = now; st.badSince = null; }
    } else if (st.state === "countdown") {
      if (!good) { st.badSince ??= now; if (now - st.badSince > 0.5) st.state = "setup"; }
      else { st.badSince = null; if (now - st.tCount >= cd) { st.state = "collect"; st.lastKeep = -1e9; status("measuring"); } }
    } else if (st.state === "collect") {
      if (good) {
        st.badSince = null;
        if (now - st.lastKeep >= INTERVAL) { st.collected.push(f); st.lastKeep = now; st.q.push({ height_frac: geo.height_frac, tilt: geo.tilt }); }
      } else {
        st.badSince ??= now;
        if (now - st.badSince > 1.5) { st.state = "setup"; st.collected = []; st.badSince = null; }   // 자세가 오래 흐트러지면 이번 회차만 다시
      }
      if (st.collected.length >= FRAMES) {
        st.rounds.push(st.collected); st.collected = [];
        if (st.rounds.length < ROUNDS) { st.state = "between"; st.betweenStart = now; st.seenAway = false; status("between"); }
        else return finish(st.rounds, quality(st.q));
      }
    } else if (st.state === "between") {
      // 다음 회차 전에 자리를 한 번 벗어났다가 다시 서기 → 서는 위치·자세가 달라져 오차가 상쇄됨
      if (issues.some((c) => ["no_person", "body_hidden", "feet_hidden", "side", "center"].includes(c))) st.seenAway = true;
      const timedOut = now - st.betweenStart > BODY_REPOSITION_TIMEOUT;
      if ((st.seenAway || timedOut) && good) { st.state = "countdown"; st.tCount = now; st.badSince = null; }
    }

    drawBody(geo, vw, vh, k, good);
    const round = st.rounds.length + 1;
    const done = (st.rounds.length * FRAMES + st.collected.length) / (ROUNDS * FRAMES);
    const step = `체형 분석 · ${round > ROUNDS ? ROUNDS : round}/${ROUNDS}회차`;
    if (st.state === "between") {
      setUi({ step: `체형 분석 · ${st.rounds.length}/${ROUNDS}회 완료`, progress: done,
        msg: st.seenAway ? "좋아요, 다시 제자리에 서 주세요" : "한 걸음 옆으로 비켜났다가",
        sub: st.seenAway ? "" : "다시 제자리에 서 주세요 — 한 번 더 재서 정확도를 높여요" });
    } else if (st.state === "countdown") {
      setUi({ step, progress: done, msg: "그대로 서 계세요", ok: true, sub: "팔은 몸에서 살짝 뗀 A자 자세",
        count: Math.max(1, Math.ceil(cd - (now - st.tCount))) });
    } else if (st.state === "collect") {
      setUi({ step, progress: done, msg: good ? "측정 중이에요 — 움직이지 마세요" : BODY_MSG[blk[0]] || "자세를 확인해 주세요", ok: good });
    } else {
      const tip = issues.includes("legs") && good ? BODY_MSG.legs : "";
      setUi({ step, progress: done, msg: blk.length ? BODY_MSG[blk[0]] : "좋아요!", ok: good,
        sub: blk.length ? "머리부터 발끝까지 화면에 나오게, 팔은 살짝 벌려 주세요" : tip });
    }
    if (DEBUG && geo.shoulder_dev !== undefined) $("sub").textContent = `어깨 ${(geo.shoulder_dev * 100).toFixed(1)}%  몸통 ${(geo.trunk_dev * 100).toFixed(1)}%`;
  };
}

function quality(q) {
  const out = {};
  for (const key of Object.keys(q[0] || {})) {
    const vals = q.map((x) => x[key]).filter((v) => Number.isFinite(v));
    if (vals.length) out[key] = Math.round(median(vals) * 1000) / 1000;
  }
  return out;
}

// ---------------------------------------------------------------------------
// 끝: 앱에 결과 전달 (앱 밖이면 직접 판정해서 보여 줌)
// ---------------------------------------------------------------------------
let stopAll = () => {};

async function finish(rounds, q) {
  stopAll();
  const payload = { rounds, quality: q };
  if (embedded()) {
    post({ type: "moda-live-result", mode: MODE, payload });
    showPanel("측정이 끝났어요", "결과를 분석하고 있어요", { spin: true });
    return;
  }
  showPanel("측정이 끝났어요", "판정 중…", { spin: true });
  try {
    const body = MODE === "body" ? { ...payload, height_cm: TEST_HEIGHT } : payload;
    const r = await fetch(`/v1/live/${MODE}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const res = await r.json();
    const label = MODE === "face" ? res.face_shape : res.body_type;
    showPanel(res.usable ? `판정 결과: ${label}` : "판정하지 못했어요",
      MODE === "body" ? `(테스트용 키 ${TEST_HEIGHT}cm — 주소에 &height=키 로 바꿀 수 있어요)` : (res.reason || ""),
      { json: JSON.stringify(res, null, 2), button: { label: "다시 측정하기", onClick: () => location.reload() } });
  } catch (e) {
    showPanel("판정 서버에 연결하지 못했어요", String(e), { button: { label: "다시 측정하기", onClick: () => location.reload() } });
  }
}

async function main() {
  status("loading");
  if (!navigator.mediaDevices?.getUserMedia) {
    status("error", { code: "CAMERA_UNSUPPORTED", message: "이 환경에서는 카메라를 쓸 수 없어요 (https 주소가 필요해요)" });
    return showPanel("카메라를 쓸 수 없어요", "https 주소로 열었는지 확인해 주세요.", { button: { label: "다시 시도", onClick: () => location.reload() } });
  }
  let landmarker, stream;
  try {
    [landmarker, stream] = await Promise.all([loadVision(), openCamera()]);
  } catch (e) {
    const denied = e && (e.name === "NotAllowedError" || e.name === "SecurityError");
    status("error", { code: denied ? "CAMERA_DENIED" : "LOAD_FAILED", message: String(e && e.message || e) });
    return showPanel(denied ? "카메라 권한이 필요해요" : "분석 준비에 실패했어요",
      denied ? "설정에서 카메라 권한을 허용한 뒤 다시 시도해 주세요." : "인터넷 연결을 확인한 뒤 다시 시도해 주세요.",
      { button: { label: "다시 시도", onClick: () => location.reload() } });
  }
  $("panel").hidden = true;
  $("stage").hidden = false;
  status("ready");
  status("measuring");
  const step = MODE === "face" ? faceFlow(landmarker) : bodyFlow(landmarker);
  let running = true;
  stopAll = () => { running = false; stream.getTracks().forEach((t) => t.stop()); try { landmarker.close(); } catch (e) { /* 이미 닫힘 */ } };
  const tick = () => {
    if (!running) return;
    try { if (video.readyState >= 2 && video.videoWidth) step(); }
    catch (e) { console.error(e); }
    if (running) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

main();
