package com.moda.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moda.ai.model.BodyAnalysisResult;
import com.moda.ai.model.FaceAnalysisResult;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.global.error.BusinessException;
import com.moda.global.validation.EnumValueValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@code moda.ai.mode=mock} 분석 클라이언트. 사진 내용은 보지 않고 Mock 헤더(api-spec.md 1.5)로 결과를 결정합니다.
 * <p>AI 서버 응답과 같은 snake_case JSON을 만들어 {@link AiResponseParser}로 넘기므로 실제 연동과 같은 검증·정규화 경로를 탑니다.
 *
 * <table>
 *   <tr><th>헤더</th><th>효과</th><th>기본값</th></tr>
 *   <tr><td>X-Mock-Face-Shape</td><td>얼굴형 지정</td><td>oval</td></tr>
 *   <tr><td>X-Mock-Body-Type</td><td>체형 지정</td><td>standard</td></tr>
 *   <tr><td>X-Mock-Unusable</td><td>usable=false + 재촬영 경고 (FACE_YAW_EXCEEDED / BODY_LOW_VISIBILITY)</td><td>false</td></tr>
 *   <tr><td>X-Mock-Borderline</td><td>borderline=true, confidence=0.52</td><td>false</td></tr>
 *   <tr><td>X-Mock-Body-Minimal</td><td>체형 필수 필드만 반환</td><td>false</td></tr>
 *   <tr><td>X-Mock-Delay-Ms</td><td>응답 지연 0~10000</td><td>800 ({@code moda.ai.mock-default-delay})</td></tr>
 * </table>
 */
public class MockAnalysisClient implements AnalysisClient {

    static final double NORMAL_CONFIDENCE_FACE = 0.64;
    static final double NORMAL_CONFIDENCE_BODY = 0.68;
    static final double BORDERLINE_CONFIDENCE = 0.52;
    static final int MAX_DELAY_MS = 10_000;

    private static final double[] FACE_REST = {0.21, 0.09, 0.03, 0.02, 0.01};
    private static final double[] FACE_REST_BORDERLINE = {0.30, 0.10, 0.05, 0.02, 0.01};
    private static final double[] BODY_REST = {0.22, 0.07, 0.03};
    private static final double[] BODY_REST_BORDERLINE = {0.30, 0.12, 0.06};

    /** length_ratio, forehead_ratio, jaw_ratio, chin_ratio, jaw_angle_deg */
    private static final Map<FaceShape, double[]> FACE_FEATURES = Map.of(
            FaceShape.DIAMOND, new double[]{1.30, 0.86, 0.72, 0.58, 138.0},
            FaceShape.HEART, new double[]{1.18, 0.93, 0.69, 0.62, 141.5},
            FaceShape.OVAL, new double[]{1.35, 0.90, 0.80, 0.64, 132.0},
            FaceShape.RECTANGLE, new double[]{1.42, 0.95, 0.92, 0.75, 122.0},
            FaceShape.ROUND, new double[]{1.08, 0.92, 0.88, 0.72, 128.0},
            FaceShape.LONG, new double[]{1.55, 0.90, 0.82, 0.66, 130.0});

    /** 얼굴형별 고정 reason (api-spec.md 1.5 / 부록 A.2 v8). 표현 가이드(R-11) 준수. */
    static final Map<FaceShape, String> FACE_REASONS = Map.of(
            FaceShape.DIAMOND, "광대 폭 대비 이마·턱 너비 비율이 평균보다 낮아, 광대 라인이 또렷한 다이아몬드형 특징이 보입니다.",
            FaceShape.HEART, "평균 대비 이마 너비 비율이 높고 턱끝 너비 비율이 낮아, 이마에서 턱끝으로 갸름하게 모이는 하트형 특징이 보입니다.",
            FaceShape.OVAL, "얼굴 길이·이마·턱 너비 비율이 평균 범위에 고르게 있어, 균형 잡힌 계란형 특징이 보입니다.",
            FaceShape.RECTANGLE, "평균 대비 얼굴 길이 비율과 턱 너비 비율이 모두 높아, 세로 비율이 시원하고 턱선이 또렷한 직사각형 특징이 보입니다.",
            FaceShape.ROUND, "평균 대비 얼굴 길이 비율이 낮고 턱 각도가 완만해, 곡선이 부드럽게 이어지는 둥근형 특징이 보입니다.",
            FaceShape.LONG, "평균 대비 얼굴 길이 비율이 높아, 세로 라인이 시원하게 뻗은 긴형 특징이 보입니다.");

    private static final String[] SK_KEYS = {"bideltoid_width", "waist_width", "hip_width", "shoulder_height", "crotch_height", "arm_length"};
    /** Size Korea 20대 남성 평균(mm) — Mock 비교용 */
    private static final int[] SK_REF_MM = {470, 271, 330, 1399, 794, 581};
    /** 체형별 항목 편차 (SK_KEYS 순서). 체형 판정 기준(style-rules.md 1.3)과 맞도록 구성 */
    private static final Map<BodyType, double[]> BODY_DEVIATIONS = Map.of(
            BodyType.STANDARD, new double[]{0.010, -0.005, 0.000, 0.005, 0.000, 0.002},
            BodyType.SMALL_INV_TRIANGLE, new double[]{0.040, -0.041, -0.020, 0.010, 0.020, 0.001},
            BodyType.LARGE_RECTANGLE, new double[]{0.050, 0.100, 0.090, 0.000, -0.010, 0.005},
            BodyType.RECTANGLE, new double[]{0.000, 0.030, 0.025, 0.000, 0.010, 0.000});

    private final ObjectMapper objectMapper;
    private final AiResponseParser parser;
    private final Duration defaultDelay;

    public MockAnalysisClient(ObjectMapper objectMapper, AiResponseParser parser, Duration defaultDelay) {
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.defaultDelay = defaultDelay;
    }

    @Override
    public FaceAnalysisResult analyzeFace(AnalysisImage image, AnalysisOptions options) {
        FaceShape shape = parseKey(options.mockFaceShape(), AnalysisOptions.H_FACE_SHAPE, FaceShape.class, FaceShape::findByKey, FaceShape.OVAL);
        boolean unusable = parseFlag(options.mockUnusable(), AnalysisOptions.H_UNUSABLE);
        boolean borderline = parseFlag(options.mockBorderline(), AnalysisOptions.H_BORDERLINE);
        delay(options.mockDelayMs());

        ObjectNode root = objectMapper.createObjectNode();
        if (unusable) {
            root.put("usable", false);
            root.putNull("face_shape");
            root.putArray("warnings").add("FACE_YAW_EXCEEDED");
            root.put("model_version", "mock-face-0.1");
            return parser.parseFace(root);
        }
        double confidence = borderline ? BORDERLINE_CONFIDENCE : NORMAL_CONFIDENCE_FACE;
        root.put("usable", true);
        root.put("face_shape", shape.key());
        root.put("reason", FACE_REASONS.get(shape));
        root.put("confidence", confidence);
        root.put("borderline", borderline);
        ArrayNode candidates = root.putArray("candidates");
        candidates.addObject().put("key", shape.key()).put("prob", confidence);
        double[] rest = borderline ? FACE_REST_BORDERLINE : FACE_REST;
        int i = 0;
        for (FaceShape other : FaceShape.values()) {
            if (other != shape) {
                candidates.addObject().put("key", other.key()).put("prob", rest[i++]);
            }
        }
        root.putArray("warnings");
        double[] f = FACE_FEATURES.get(shape);
        ObjectNode features = root.putObject("features");
        features.put("length_ratio", f[0]).put("forehead_ratio", f[1]).put("jaw_ratio", f[2]).put("chin_ratio", f[3])
                .put("jaw_angle_deg", f[4]);
        root.putNull("compare_to_size_korea");
        root.putObject("quality").put("yaw_deg", 3.2).put("pitch_deg", 5.1).put("face_width_px", 388);
        root.put("model_version", "mock-face-0.1");
        return parser.parseFace(root);
    }

    @Override
    public BodyAnalysisResult analyzeBody(AnalysisImage image, int heightCm, AnalysisOptions options) {
        BodyType type = parseKey(options.mockBodyType(), AnalysisOptions.H_BODY_TYPE, BodyType.class, BodyType::findByKey, BodyType.STANDARD);
        boolean unusable = parseFlag(options.mockUnusable(), AnalysisOptions.H_UNUSABLE);
        boolean borderline = parseFlag(options.mockBorderline(), AnalysisOptions.H_BORDERLINE);
        boolean minimal = parseFlag(options.mockBodyMinimal(), AnalysisOptions.H_BODY_MINIMAL);
        delay(options.mockDelayMs());

        ObjectNode root = objectMapper.createObjectNode();
        if (unusable) {
            root.put("usable", false);
            root.putNull("body_type");
            root.putArray("warnings").add("BODY_LOW_VISIBILITY");
            return parser.parseBody(root);
        }
        root.put("usable", true);
        root.put("body_type", type.key());
        root.putArray("warnings");
        if (minimal) {                                                                // 유료 API가 체형 판정만 주는 경우 (A.3-2)
            return parser.parseBody(root);
        }
        double confidence = borderline ? BORDERLINE_CONFIDENCE : NORMAL_CONFIDENCE_BODY;
        root.put("confidence", confidence);
        root.put("borderline", borderline);
        ArrayNode candidates = root.putArray("candidates");
        candidates.addObject().put("key", type.key()).put("prob", confidence);
        double[] rest = borderline ? BODY_REST_BORDERLINE : BODY_REST;
        int i = 0;
        for (BodyType other : BodyType.values()) {
            if (other != type) {
                candidates.addObject().put("key", other.key()).put("prob", rest[i++]);
            }
        }
        double[] dev = BODY_DEVIATIONS.get(type);
        double trunk = round3((dev[1] + dev[2]) / 2);
        root.putObject("features")
                .put("shoulder_dev", dev[0])
                .put("trunk_dev", trunk)
                .put("shoulder_minus_trunk", round3(dev[0] - trunk));
        ObjectNode compare = root.putObject("compare_to_size_korea");
        compare.put("reference", "SIZE_KOREA_20S_MALE");
        compare.put("height_cm", heightCm);
        ArrayNode items = compare.putArray("items");
        for (int k = 0; k < SK_KEYS.length; k++) {
            items.addObject()
                    .put("key", SK_KEYS[k])
                    .put("user_mm", Math.round(SK_REF_MM[k] * (1 + dev[k])))
                    .put("ref_mm", SK_REF_MM[k])
                    .put("deviation", dev[k]);
        }
        root.putObject("quality").put("min_visibility", 0.83).put("shoulder_tilt_deg", 2.1);
        root.put("model_version", "mock-body-0.1");
        return parser.parseBody(root);
    }

    // ------------------------------------------------------------------ ③-L · ④-L 실시간 분석

    /** Mock은 측정값을 보지 않으므로 기존 사진 분석과 같은 결과를 냅니다 (X-Mock-* 헤더 동일 적용, ai/LIVE_INTEGRATION.md 2.3). */
    @Override
    public FaceAnalysisResult analyzeFaceLive(JsonNode measurement, String requestId, AnalysisOptions options) {
        return analyzeFace(null, options);
    }

    @Override
    public BodyAnalysisResult analyzeBodyLive(JsonNode measurement, int heightCm, String requestId, AnalysisOptions options) {
        return analyzeBody(null, heightCm, options);
    }

    // ------------------------------------------------------------------ header parsing

    private static <E extends Enum<E>> E parseKey(String raw, String header, Class<E> type,
                                                  Function<String, Optional<E>> finder, E defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return finder.apply(raw.strip()).orElseThrow(() -> BusinessException.invalidField(header,
                String.join(", ", EnumValueValidator.allowedValues(type)) + " 중 하나여야 합니다.", raw));
    }

    private static boolean parseFlag(String raw, String header) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String v = raw.strip().toLowerCase(Locale.ROOT);
        if (v.equals("true")) {
            return true;
        }
        if (v.equals("false")) {
            return false;
        }
        throw BusinessException.invalidField(header, "true 또는 false여야 합니다.", raw);
    }

    private void delay(String raw) {
        long ms = defaultDelay.toMillis();
        if (raw != null && !raw.isBlank()) {
            try {
                ms = Long.parseLong(raw.strip());
            } catch (NumberFormatException e) {
                ms = -1;
            }
            if (ms < 0 || ms > MAX_DELAY_MS) {
                throw BusinessException.invalidField(AnalysisOptions.H_DELAY_MS, "0 이상 " + MAX_DELAY_MS + " 이하의 정수여야 합니다.", raw);
            }
        }
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
