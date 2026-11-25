package com.moda.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.ai.model.AnalysisValues.CandidateValue;
import com.moda.ai.model.AnalysisValues.FeatureValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaItemValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaSummaryValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaValue;
import com.moda.ai.model.BodyAnalysisResult;
import com.moda.ai.model.FaceAnalysisResult;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 응답(snake_case JSON) 검증·정규화 (docs/api-spec.md 부록 A.2 ~ A.4, A.6). Mock·External 클라이언트가 같은 경로를 사용합니다.
 * <ul>
 *   <li>얼굴: {@code usable}·{@code face_shape}·{@code warnings} 필수. {@code usable=true}이면 {@code confidence}·{@code borderline}·
 *       {@code candidates}·{@code features}도 필수 → 누락·형식 오류 시 {@code 502 AI_BAD_RESPONSE}.
 *       {@code reason}(v8)은 선택 — 누락·{@code null}·형식 오류여도 {@code null} 처리(502 없음)</li>
 *   <li>체형: {@code usable}·{@code body_type}·{@code warnings}만 필수. 나머지 선택 필드는 누락·형식 오류여도 {@code null}/{@code []}
 *       처리 후 경고 로그</li>
 * </ul>
 */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    static final Set<String> SIZE_KOREA_ITEM_KEYS = Set.of(
            "bideltoid_width", "waist_width", "hip_width", "shoulder_height", "crotch_height", "arm_length");

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode readTree(String body, String what) {
        if (body == null || body.isBlank()) {
            throw bad(what, "응답 본문이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw bad(what, "JSON 파싱 실패: " + e.getOriginalMessage());
        }
    }

    // ------------------------------------------------------------------ 얼굴 (A.2)

    public FaceAnalysisResult parseFace(JsonNode root) {
        String what = "face";
        requireObject(root, what);
        boolean usable = requireBoolean(root, "usable", what);
        List<String> warnings = requireStringArray(root, "warnings", what);
        String modelVersion = optionalText(root, "model_version");
        if (!usable) {
            return new FaceAnalysisResult(false, null, null, null, null, List.of(), warnings, List.of(), modelVersion);
        }
        FaceShape shape = requireKey(root, "face_shape", FaceShape::findByKey, what);
        double confidence = requireProbability(root, "confidence", what);
        boolean borderline = requireBoolean(root, "borderline", what);
        List<CandidateValue> candidates = parseCandidates(root.get("candidates"), k -> FaceShape.findByKey(k).isPresent())
                .filter(list -> !list.isEmpty())
                .orElseThrow(() -> bad(what, "candidates가 없거나 형식이 올바르지 않습니다."));
        List<FeatureValue> features = parseFeatures(root.get("features"))
                .orElseThrow(() -> bad(what, "features가 없거나 형식이 올바르지 않습니다."));
        String reason = optionalReason(root);
        return new FaceAnalysisResult(true, shape, confidence, borderline, reason, candidates, warnings, features, modelVersion);
    }

    // ------------------------------------------------------------------ 체형 (A.3)

    public BodyAnalysisResult parseBody(JsonNode root) {
        String what = "body";
        requireObject(root, what);
        boolean usable = requireBoolean(root, "usable", what);
        List<String> warnings = requireStringArray(root, "warnings", what);
        String modelVersion = optionalText(root, "model_version");
        if (!usable) {
            return new BodyAnalysisResult(false, null, null, null, List.of(), warnings, List.of(), null, modelVersion);
        }
        BodyType type = requireKey(root, "body_type", BodyType::findByKey, what);

        Double confidence = optionalProbability(root, "confidence");
        Boolean borderline = optional(root, "borderline", JsonNode::isBoolean, JsonNode::asBoolean);
        List<CandidateValue> candidates = optionalList(root, "candidates",
                n -> parseCandidates(n, k -> BodyType.findByKey(k).isPresent()));
        List<FeatureValue> features = optionalList(root, "features", this::parseFeatures);
        SizeKoreaValue compare = null;
        JsonNode compareNode = root.get("compare_to_size_korea");
        if (isPresent(compareNode)) {
            compare = parseSizeKorea(compareNode, features).orElse(null);
            if (compare == null) {
                log.warn("[AI body] compare_to_size_korea 형식 오류 → null 처리");
            }
        }
        return new BodyAnalysisResult(true, type, confidence, borderline, candidates, warnings, features, compare, modelVersion);
    }

    // ------------------------------------------------------------------ 공통 파서

    private Optional<List<CandidateValue>> parseCandidates(JsonNode node, Function<String, Boolean> validKey) {
        if (node == null || !node.isArray()) {
            return Optional.empty();
        }
        List<CandidateValue> list = new ArrayList<>();
        for (JsonNode c : node) {
            JsonNode key = c.get("key");
            JsonNode prob = c.get("prob");
            if (key == null || !key.isTextual() || !validKey.apply(key.asText()) || prob == null || !prob.isNumber()
                    || prob.asDouble() < 0 || prob.asDouble() > 1) {
                return Optional.empty();
            }
            list.add(new CandidateValue(key.asText(), round3(prob.asDouble())));
        }
        list.sort(Comparator.comparingDouble(CandidateValue::probability).reversed());
        return Optional.of(List.copyOf(list));
    }

    private Optional<List<FeatureValue>> parseFeatures(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        List<FeatureValue> list = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            if (!e.getValue().isNumber()) {
                return Optional.empty();
            }
            list.add(new FeatureValue(e.getKey(), e.getValue().asDouble()));
        }
        return Optional.of(List.copyOf(list));
    }

    /**
     * {@code summary}는 AI가 {@code summary.shoulder_deviation/trunk_deviation}으로 주면 사용하고,
     * 없으면 features의 {@code shoulder_dev}·{@code trunk_dev}로 채웁니다. 둘 다 없으면 {@code null}.
     */
    private Optional<SizeKoreaValue> parseSizeKorea(JsonNode node, List<FeatureValue> features) {
        if (!node.isObject()) {
            return Optional.empty();
        }
        JsonNode reference = node.get("reference");
        JsonNode items = node.get("items");
        if (reference == null || !reference.isTextual() || reference.asText().isBlank() || items == null || !items.isArray()
                || items.isEmpty()) {
            return Optional.empty();
        }
        List<SizeKoreaItemValue> list = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode key = item.get("key");
            JsonNode user = item.get("user_mm");
            JsonNode ref = item.get("ref_mm");
            JsonNode dev = item.get("deviation");
            if (key == null || !key.isTextual() || !SIZE_KOREA_ITEM_KEYS.contains(key.asText())
                    || user == null || !user.isNumber() || ref == null || !ref.isNumber() || dev == null || !dev.isNumber()) {
                return Optional.empty();
            }
            list.add(new SizeKoreaItemValue(key.asText(), (int) Math.round(user.asDouble()), (int) Math.round(ref.asDouble()),
                    round3(dev.asDouble())));
        }
        JsonNode height = node.get("height_cm");
        Integer heightCm = height != null && height.isNumber() ? (int) Math.round(height.asDouble()) : null;

        SizeKoreaSummaryValue summary = null;
        JsonNode s = node.get("summary");
        if (s != null && s.isObject() && s.path("shoulder_deviation").isNumber() && s.path("trunk_deviation").isNumber()) {
            summary = new SizeKoreaSummaryValue(round3(s.get("shoulder_deviation").asDouble()), round3(s.get("trunk_deviation").asDouble()));
        } else {
            Double shoulder = featureValue(features, "shoulder_dev");
            Double trunk = featureValue(features, "trunk_dev");
            if (shoulder != null && trunk != null) {
                summary = new SizeKoreaSummaryValue(round3(shoulder), round3(trunk));
            }
        }
        return Optional.of(new SizeKoreaValue(reference.asText(), heightCm, List.copyOf(list), summary));
    }

    private static Double featureValue(List<FeatureValue> features, String key) {
        return features.stream().filter(f -> f.key().equals(key)).map(FeatureValue::value).findFirst().orElse(null);
    }

    private String optionalReason(JsonNode root) {
        JsonNode node = root.get("reason");
        if (!isPresent(node)) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            log.warn("[AI face] reason 형식 오류 → null 처리");
            return null;
        }
        return node.asText().strip();
    }

    private <T> T optional(JsonNode root, String field, Function<JsonNode, Boolean> typeCheck, Function<JsonNode, T> read) {
        JsonNode node = root.get(field);
        if (!isPresent(node)) {
            return null;
        }
        if (!typeCheck.apply(node)) {
            log.warn("[AI body] {} 형식 오류 → null 처리", field);
            return null;
        }
        return read.apply(node);
    }

    private Double optionalProbability(JsonNode root, String field) {
        Double v = optional(root, field, JsonNode::isNumber, JsonNode::asDouble);
        if (v != null && (v < 0 || v > 1)) {
            log.warn("[AI body] {} 범위 오류({}) → null 처리", field, v);
            return null;
        }
        return v == null ? null : round3(v);
    }

    private <T> List<T> optionalList(JsonNode root, String field, Function<JsonNode, Optional<List<T>>> parser) {
        JsonNode node = root.get(field);
        if (!isPresent(node)) {
            return List.of();
        }
        Optional<List<T>> parsed = parser.apply(node);
        if (parsed.isEmpty()) {
            log.warn("[AI body] {} 형식 오류 → [] 처리", field);
        }
        return parsed.orElse(List.of());
    }

    private static void requireObject(JsonNode root, String what) {
        if (root == null || !root.isObject()) {
            throw bad(what, "응답이 JSON 객체가 아닙니다.");
        }
    }

    private static boolean requireBoolean(JsonNode root, String field, String what) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) {
            throw bad(what, field + " 필드가 없거나 boolean이 아닙니다.");
        }
        return node.asBoolean();
    }

    private static double requireProbability(JsonNode root, String field, String what) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber() || node.asDouble() < 0 || node.asDouble() > 1) {
            throw bad(what, field + " 필드가 없거나 0~1 숫자가 아닙니다.");
        }
        return round3(node.asDouble());
    }

    private static List<String> requireStringArray(JsonNode root, String field, String what) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw bad(what, field + " 필드가 없거나 배열이 아닙니다.");
        }
        List<String> list = new ArrayList<>();
        for (JsonNode n : node) {
            if (!n.isTextual()) {
                throw bad(what, field + " 배열에 문자열이 아닌 값이 있습니다.");
            }
            list.add(n.asText());
        }
        return List.copyOf(list);
    }

    private static <E> E requireKey(JsonNode root, String field, Function<String, Optional<E>> finder, String what) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            throw bad(what, field + " 필드가 없습니다 (usable=true).");
        }
        return finder.apply(node.asText()).orElseThrow(() -> bad(what, "정의되지 않은 " + field + " Key: " + node.asText()));
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static boolean isPresent(JsonNode node) {
        return node != null && !node.isNull() && !node.isMissingNode();
    }

    static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static BusinessException bad(String what, String detail) {
        log.warn("[AI {}] AI_BAD_RESPONSE: {}", what, detail);
        return new BusinessException(ErrorCode.AI_BAD_RESPONSE, ErrorCode.AI_BAD_RESPONSE.defaultMessage());
    }
}
