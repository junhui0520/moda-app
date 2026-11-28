package com.moda.seed;

import com.moda.common.enums.AgeBand;
import com.moda.common.enums.BmiBand;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.FashionStyleCode;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairStyleCode;
import com.moda.common.enums.HairThickness;
import com.moda.common.enums.HeightBand;
import com.moda.common.enums.KeyedEnum;
import com.moda.common.enums.TypeCodes;
import com.moda.common.enums.WarningSeverity;
import com.moda.seed.dto.AnalysisLabelsSeed;
import com.moda.seed.dto.CatalogSeed;
import com.moda.seed.dto.RuleSeed;
import com.moda.seed.dto.SeedBundle;
import com.moda.seed.dto.TypeDefinitionsSeed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 시드 JSON 5종 검증 (docs/style-rules.md 5.3 적재 규칙 2).
 * <p>개수(유형 24·얼굴형 6·체형 4·헤어 5·패션 9), (bodyType, faceShape) 24개 조합 1회씩, typeCode 공식 일치, 알 수 없는 Key,
 * 점수표 완전성, 근거 문구 코드 형식·누락을 검사하고 오류를 모두 모아 {@link SeedValidationException}으로 던집니다.
 */
@Component
public class SeedValidator {

    public static final int TYPE_COUNT = TypeCodes.TYPE_COUNT;
    public static final int FACE_SHAPE_COUNT = FaceShape.values().length;
    public static final int BODY_TYPE_COUNT = BodyType.values().length;
    public static final int HAIR_STYLE_COUNT = HairStyleCode.values().length;
    public static final int FASHION_STYLE_COUNT = FashionStyleCode.values().length;

    public static final int TYPE_NAME_MAX_LENGTH = 20;
    public static final int MIN_STYLE_POINTS = 2;
    public static final int ITEM_KEYWORD_COUNT = 5;
    public static final int BASE_SCORE_MIN = 0;
    public static final int BASE_SCORE_MAX = 5;
    /** 기본 점수가 이 값 이상이면 전용 문구, 미만이면 일반 문구 (style-rules.md 3.3, 4.3). */
    public static final int SPECIFIC_REASON_MIN_BASE_SCORE = 4;
    public static final String HEADLINE_PLACEHOLDER = "{typeName}";

    static final Set<String> FEATURE_UNITS = Set.of("ratio", "deg", "percent");
    static final Set<String> FEATURE_TARGETS = Set.of("FACE", "BODY");
    static final Set<String> WARNING_TARGETS = Set.of("FACE", "BODY", "COMMON");

    /** api-spec.md 부록 A.4 features 라벨 표 (키 추가는 허용, 아래 키는 필수). */
    public static final List<String> REQUIRED_FEATURE_KEYS = List.of(
            "length_ratio", "forehead_ratio", "jaw_ratio", "chin_ratio", "jaw_angle_deg",
            "shoulder_dev", "trunk_dev", "shoulder_minus_trunk");

    /** api-spec.md 3.9 / 부록 A.4 Size Korea 항목 6종 (정확히 일치해야 함). */
    public static final List<String> SIZE_KOREA_ITEM_KEYS = List.of(
            "bideltoid_width", "waist_width", "hip_width", "shoulder_height", "crotch_height", "arm_length");

    /** api-spec.md 부록 A.5 경고 코드 16종 (필수). v1.3: 실시간 분석 {@code BODY_LEGS_CLOSED}, {@code LIVE_NOT_ENOUGH_FRAMES} 추가. */
    public static final List<String> REQUIRED_WARNING_CODES = List.of(
            "FACE_NOT_DETECTED", "MULTIPLE_FACES", "FACE_YAW_EXCEEDED", "FACE_PITCH_EXCEEDED", "FACE_TOO_SMALL",
            "MOUTH_OPEN", "SMILING", "FACE_BORDERLINE",
            "BODY_NOT_DETECTED", "MULTIPLE_PEOPLE", "BODY_LOW_VISIBILITY", "SHOULDER_TILT", "BODY_BORDERLINE",
            "BODY_LEGS_CLOSED", "LIVE_NOT_ENOUGH_FRAMES",
            "ANALYSIS_UNUSABLE");

    private static final Map<String, String> HAIR_FACTOR_TOKENS = Map.of(
            "hairThickness", "THICKNESS", "hairCurl", "CURL", "hairDensity", "DENSITY");

    static final RuleSpec HAIR_SPEC = new RuleSpec(
            SeedFile.HAIR_RULES, "HAIR", "faceShape",
            keys(FaceShape.values()),
            names(HairStyleCode.values()),
            orderedMap("hairThickness", names(HairThickness.values()),
                    "hairCurl", names(HairCurl.values()),
                    "hairDensity", names(HairDensity.values())),
            (baseKey, style) -> "FACE_" + baseKey.toUpperCase(Locale.ROOT) + "_" + style,
            style -> "HAIR_GENERIC_" + style,
            (factor, value, style) -> "ADJ_" + HAIR_FACTOR_TOKENS.get(factor) + "_" + value + "_" + style);

    static final RuleSpec FASHION_SPEC = new RuleSpec(
            SeedFile.FASHION_RULES, "FASHION", "bodyType",
            keys(BodyType.values()),
            names(FashionStyleCode.values()),
            orderedMap("bmi", names(BmiBand.values()),
                    "height", names(HeightBand.values()),
                    "age", names(AgeBand.values())),
            (baseKey, style) -> "BODY_" + baseKey.toUpperCase(Locale.ROOT) + "_" + style,
            style -> "FASHION_GENERIC_" + style,
            (factor, value, style) -> "ADJ_" + value + "_" + style);

    public void validate(SeedBundle bundle) {
        Errors errors = new Errors();
        if (bundle == null) {
            throw new SeedValidationException(List.of("시드 묶음이 null입니다."));
        }
        validateRuleVersions(bundle, errors);
        validateTypeDefinitions(bundle.typeDefinitions(), errors);
        validateCatalog(bundle.catalog(), errors);
        Map<String, SeedFile> reasonCodes = new HashMap<>();
        validateRules(bundle.hairRules(), HAIR_SPEC, reasonCodes, errors);
        validateRules(bundle.fashionRules(), FASHION_SPEC, reasonCodes, errors);
        validateAnalysisLabels(bundle.analysisLabels(), errors);
        if (!errors.isEmpty()) {
            throw new SeedValidationException(errors.list);
        }
    }

    // ------------------------------------------------------------------ ruleVersion

    private void validateRuleVersions(SeedBundle b, Errors e) {
        Map<SeedFile, String> versions = new LinkedHashMap<>();
        versions.put(SeedFile.TYPE_DEFINITIONS, b.typeDefinitions() == null ? null : b.typeDefinitions().ruleVersion());
        versions.put(SeedFile.CATALOG, b.catalog() == null ? null : b.catalog().ruleVersion());
        versions.put(SeedFile.HAIR_RULES, b.hairRules() == null ? null : b.hairRules().ruleVersion());
        versions.put(SeedFile.FASHION_RULES, b.fashionRules() == null ? null : b.fashionRules().ruleVersion());
        versions.put(SeedFile.ANALYSIS_LABELS, b.analysisLabels() == null ? null : b.analysisLabels().ruleVersion());
        versions.forEach((file, v) -> {
            if (isBlank(v)) {
                e.add(file, "ruleVersion이 비어 있습니다.");
            }
        });
        Set<String> distinct = new LinkedHashSet<>(versions.values());
        distinct.remove(null);
        if (distinct.size() > 1) {
            e.add(null, "시드 파일 간 ruleVersion이 다릅니다: " + versions);
        }
    }

    // ------------------------------------------------------------------ type-definitions.json

    private void validateTypeDefinitions(TypeDefinitionsSeed seed, Errors e) {
        SeedFile f = SeedFile.TYPE_DEFINITIONS;
        if (seed == null) {
            e.add(f, "파일 내용이 없습니다.");
            return;
        }
        if (isBlank(seed.headlineTemplate()) || !seed.headlineTemplate().contains(HEADLINE_PLACEHOLDER)) {
            e.add(f, "headlineTemplate에 " + HEADLINE_PLACEHOLDER + " 자리표시자가 필요합니다: " + seed.headlineTemplate());
        }
        List<TypeDefinitionsSeed.TypeSeed> types = seed.types();
        if (types == null) {
            e.add(f, "types 배열이 없습니다.");
            return;
        }
        if (types.size() != TYPE_COUNT) {
            e.add(f, "유형 개수는 " + TYPE_COUNT + "개여야 합니다 (현재 " + types.size() + "개).");
        }
        Set<String> codes = new HashSet<>();
        Set<String> combinations = new HashSet<>();
        for (int i = 0; i < types.size(); i++) {
            TypeDefinitionsSeed.TypeSeed t = types.get(i);
            if (t == null) {
                e.add(f, "types[" + i + "]가 null입니다.");
                continue;
            }
            String id = isBlank(t.typeCode()) ? "types[" + i + "]" : t.typeCode();
            if (isBlank(t.typeCode())) {
                e.add(f, id + ": typeCode가 비어 있습니다.");
            } else if (!codes.add(t.typeCode())) {
                e.add(f, id + ": typeCode가 중복됩니다.");
            }
            BodyType body = BodyType.findByKey(t.bodyType()).orElse(null);
            FaceShape face = FaceShape.findByKey(t.faceShape()).orElse(null);
            if (body == null) {
                e.add(f, id + ": 알 수 없는 bodyType Key '" + t.bodyType() + "'");
            }
            if (face == null) {
                e.add(f, id + ": 알 수 없는 faceShape Key '" + t.faceShape() + "'");
            }
            if (body != null && face != null) {
                if (!combinations.add(body.key() + "|" + face.key())) {
                    e.add(f, id + ": (bodyType, faceShape) 조합 (" + body.key() + ", " + face.key() + ")이 중복됩니다.");
                }
                String expected = TypeCodes.of(body, face);
                if (!expected.equals(t.typeCode())) {
                    e.add(f, id + ": typeCode가 공식(bodyIndex × 6 + faceIndex + 1) 결과 " + expected + "와 다릅니다.");
                }
                if (!Objects.equals(t.sortOrder(), TypeCodes.indexOf(body, face))) {
                    e.add(f, id + ": sortOrder는 " + TypeCodes.indexOf(body, face) + "이어야 합니다 (현재 " + t.sortOrder() + ").");
                }
            }
            if (isBlank(t.typeName())) {
                e.add(f, id + ": typeName이 비어 있습니다.");
            } else if (t.typeName().length() > TYPE_NAME_MAX_LENGTH) {
                e.add(f, id + ": typeName은 1~" + TYPE_NAME_MAX_LENGTH + "자여야 합니다.");
            }
            if (t.nameConfirmed() == null) {
                e.add(f, id + ": nameConfirmed가 없습니다.");
            }
            if (isBlank(t.description())) {
                e.add(f, id + ": description이 비어 있습니다.");
            }
            if (t.stylePoints() == null || t.stylePoints().size() < MIN_STYLE_POINTS) {
                e.add(f, id + ": stylePoints는 " + MIN_STYLE_POINTS + "개 이상이어야 합니다.");
            } else if (t.stylePoints().stream().anyMatch(SeedValidator::isBlank)) {
                e.add(f, id + ": stylePoints에 빈 문자열이 있습니다.");
            }
        }
        for (BodyType body : BodyType.values()) {
            for (FaceShape face : FaceShape.values()) {
                if (!combinations.contains(body.key() + "|" + face.key())) {
                    e.add(f, "(bodyType, faceShape) 조합 (" + body.key() + ", " + face.key() + ")에 해당하는 유형이 없습니다 ("
                            + TypeCodes.of(body, face) + ").");
                }
            }
        }
    }

    // ------------------------------------------------------------------ catalog.json

    private void validateCatalog(CatalogSeed seed, Errors e) {
        SeedFile f = SeedFile.CATALOG;
        if (seed == null) {
            e.add(f, "파일 내용이 없습니다.");
            return;
        }
        validateCodeList(f, "faceShapes", seed.faceShapes(), keys(FaceShape.values()), CatalogSeed.FaceShapeSeed::key, e);
        if (seed.faceShapes() != null) {
            for (CatalogSeed.FaceShapeSeed s : seed.faceShapes()) {
                if (s == null) {
                    continue;
                }
                FaceShape.findByKey(s.key()).ifPresent(face -> {
                    if (!Objects.equals(s.faceIndex(), face.index())) {
                        e.add(f, "faceShapes." + s.key() + ": faceIndex는 " + face.index() + "이어야 합니다 (현재 " + s.faceIndex() + ").");
                    }
                });
                requireText(f, "faceShapes." + s.key(), "label", s.label(), e);
                requireText(f, "faceShapes." + s.key(), "description", s.description(), e);
            }
        }

        validateCodeList(f, "bodyTypes", seed.bodyTypes(), keys(BodyType.values()), CatalogSeed.BodyTypeSeed::key, e);
        if (seed.bodyTypes() != null) {
            for (CatalogSeed.BodyTypeSeed s : seed.bodyTypes()) {
                if (s == null) {
                    continue;
                }
                BodyType.findByKey(s.key()).ifPresent(body -> {
                    if (!Objects.equals(s.bodyIndex(), body.index())) {
                        e.add(f, "bodyTypes." + s.key() + ": bodyIndex는 " + body.index() + "이어야 합니다 (현재 " + s.bodyIndex() + ").");
                    }
                });
                requireText(f, "bodyTypes." + s.key(), "label", s.label(), e);
                requireText(f, "bodyTypes." + s.key(), "description", s.description(), e);
            }
        }

        List<String> hairCodes = names(HairStyleCode.values());
        validateCodeList(f, "hairStyles", seed.hairStyles(), hairCodes, CatalogSeed.HairStyleSeed::code, e);
        if (seed.hairStyles() != null) {
            for (CatalogSeed.HairStyleSeed s : seed.hairStyles()) {
                if (s == null || !hairCodes.contains(s.code())) {
                    continue;
                }
                String id = "hairStyles." + s.code();
                checkSortOrder(f, id, s.sortOrder(), hairCodes.indexOf(s.code()) + 1, e);
                requireText(f, id, "name", s.name(), e);
                requireText(f, id, "description", s.description(), e);
                checkImageKey(f, id, s.imageKey(), "hair_" + s.code().toLowerCase(Locale.ROOT), e);
            }
        }

        List<String> fashionCodes = names(FashionStyleCode.values());
        validateCodeList(f, "fashionStyles", seed.fashionStyles(), fashionCodes, CatalogSeed.FashionStyleSeed::code, e);
        if (seed.fashionStyles() != null) {
            for (CatalogSeed.FashionStyleSeed s : seed.fashionStyles()) {
                if (s == null || !fashionCodes.contains(s.code())) {
                    continue;
                }
                String id = "fashionStyles." + s.code();
                checkSortOrder(f, id, s.sortOrder(), fashionCodes.indexOf(s.code()) + 1, e);
                requireText(f, id, "name", s.name(), e);
                requireText(f, id, "description", s.description(), e);
                checkImageKey(f, id, s.imageKey(), "fashion_" + s.code().toLowerCase(Locale.ROOT), e);
                List<String> kw = s.itemKeywords();
                if (kw == null || kw.size() != ITEM_KEYWORD_COUNT) {
                    e.add(f, id + ": itemKeywords는 " + ITEM_KEYWORD_COUNT + "개여야 합니다 (현재 " + (kw == null ? 0 : kw.size()) + "개).");
                } else if (kw.stream().anyMatch(SeedValidator::isBlank)) {
                    e.add(f, id + ": itemKeywords에 빈 문자열이 있습니다.");
                } else if (new HashSet<>(kw).size() != kw.size()) {
                    e.add(f, id + ": itemKeywords가 중복됩니다.");
                }
            }
        }
    }

    // ------------------------------------------------------------------ hair-rules.json / fashion-rules.json

    private void validateRules(RuleSeed seed, RuleSpec spec, Map<String, SeedFile> reasonCodes, Errors e) {
        SeedFile f = spec.file();
        if (seed == null) {
            e.add(f, "파일 내용이 없습니다.");
            return;
        }
        if (!spec.domain().equals(seed.domain())) {
            e.add(f, "domain은 " + spec.domain() + "이어야 합니다 (현재 " + seed.domain() + ").");
        }
        if (!spec.baseFactor().equals(seed.baseFactor())) {
            e.add(f, "baseFactor는 " + spec.baseFactor() + "이어야 합니다 (현재 " + seed.baseFactor() + ").");
        }
        if (!spec.styleCodes().equals(seed.styleOrder())) {
            e.add(f, "styleOrder는 코드 선언 순서 " + spec.styleCodes() + "와 같아야 합니다 (현재 " + seed.styleOrder() + ").");
        }
        RuleSeed.ScoreRange range = seed.baseScoreRange();
        if (range == null || !Objects.equals(range.min(), BASE_SCORE_MIN) || !Objects.equals(range.max(), BASE_SCORE_MAX)) {
            e.add(f, "baseScoreRange는 {min: " + BASE_SCORE_MIN + ", max: " + BASE_SCORE_MAX + "}이어야 합니다.");
        }

        // 기본 점수표
        Map<String, Map<String, Integer>> base = seed.baseScores();
        Set<String> specificExpected = new LinkedHashSet<>();
        if (base == null) {
            e.add(f, "baseScores가 없습니다.");
        } else {
            compareKeySet(f, "baseScores", base.keySet(), spec.baseKeys(), e);
            for (String baseKey : spec.baseKeys()) {
                Map<String, Integer> row = base.get(baseKey);
                if (row == null) {
                    continue;
                }
                compareKeySet(f, "baseScores." + baseKey, row.keySet(), spec.styleCodes(), e);
                for (String style : spec.styleCodes()) {
                    Integer score = row.get(style);
                    if (score == null) {
                        continue;
                    }
                    if (score < BASE_SCORE_MIN || score > BASE_SCORE_MAX) {
                        e.add(f, "baseScores." + baseKey + "." + style + ": 기본 점수는 " + BASE_SCORE_MIN + "~" + BASE_SCORE_MAX
                                + " 범위여야 합니다 (현재 " + score + ").");
                    } else if (score >= SPECIFIC_REASON_MIN_BASE_SCORE) {
                        specificExpected.add(baseKey + "|" + style);
                    }
                }
            }
        }
        if (seed.baseRationale() == null) {
            e.add(f, "baseRationale이 없습니다.");
        } else {
            compareKeySet(f, "baseRationale", seed.baseRationale().keySet(), spec.baseKeys(), e);
            seed.baseRationale().forEach((k, v) -> requireText(f, "baseRationale." + k, "방향", v, e));
        }

        // 보정표
        Set<String> positiveExpected = new LinkedHashSet<>();
        Set<String> adjustmentRows = new HashSet<>();
        if (seed.adjustments() == null) {
            e.add(f, "adjustments가 없습니다.");
        } else {
            for (RuleSeed.AdjustmentRowSeed row : seed.adjustments()) {
                if (row == null) {
                    e.add(f, "adjustments에 null 행이 있습니다.");
                    continue;
                }
                String id = "adjustments[" + row.factor() + "=" + row.value() + "]";
                List<String> allowed = spec.adjustmentValues().get(row.factor());
                if (allowed == null) {
                    e.add(f, id + ": 알 수 없는 factor '" + row.factor() + "' (허용: " + spec.adjustmentValues().keySet() + ")");
                    continue;
                }
                if (!allowed.contains(row.value())) {
                    e.add(f, id + ": 알 수 없는 값 '" + row.value() + "' (허용: " + allowed + ")");
                    continue;
                }
                if (!adjustmentRows.add(row.factor() + "|" + row.value())) {
                    e.add(f, id + ": 보정 행이 중복됩니다.");
                    continue;
                }
                if (row.deltas() == null) {
                    e.add(f, id + ": deltas가 없습니다.");
                    continue;
                }
                compareKeySet(f, id + ".deltas", row.deltas().keySet(), spec.styleCodes(), e);
                row.deltas().forEach((style, delta) -> {
                    if (delta == null) {
                        e.add(f, id + "." + style + ": delta가 null입니다.");
                    } else if (delta > 0 && spec.styleCodes().contains(style)) {
                        positiveExpected.add(row.factor() + "|" + row.value() + "|" + style);
                    }
                });
            }
            spec.adjustmentValues().forEach((factor, values) -> values.forEach(value -> {
                if (!adjustmentRows.contains(factor + "|" + value)) {
                    e.add(f, "adjustments: " + factor + "=" + value + " 보정 행이 없습니다.");
                }
            }));
        }

        // 기본 점수 전용 문구 (4점 이상 조합)
        Set<String> specificActual = new LinkedHashSet<>();
        if (seed.baseReasons() == null) {
            e.add(f, "baseReasons가 없습니다.");
        } else {
            for (RuleSeed.BaseReasonSeed r : seed.baseReasons()) {
                if (r == null) {
                    continue;
                }
                String id = "baseReasons." + r.reasonCode();
                registerReasonCode(f, r.reasonCode(), reasonCodes, e);
                requireText(f, id, "text", r.text(), e);
                if (!spec.baseKeys().contains(r.baseKey())) {
                    e.add(f, id + ": 알 수 없는 " + spec.baseFactor() + " Key '" + r.baseKey() + "'");
                    continue;
                }
                if (!spec.styleCodes().contains(r.styleCode())) {
                    e.add(f, id + ": 알 수 없는 styleCode '" + r.styleCode() + "'");
                    continue;
                }
                checkReasonCode(f, id, r.reasonCode(), spec.baseReasonCode().apply(r.baseKey(), r.styleCode()), e);
                specificActual.add(r.baseKey() + "|" + r.styleCode());
            }
            compareCoverage(f, "기본 점수 " + SPECIFIC_REASON_MIN_BASE_SCORE + "점 이상 조합의 전용 문구(baseReasons)",
                    specificActual, specificExpected, e);
        }

        // 기본 점수 일반 문구 (스타일별 1개)
        Set<String> genericActual = new LinkedHashSet<>();
        if (seed.genericReasons() == null) {
            e.add(f, "genericReasons가 없습니다.");
        } else {
            for (RuleSeed.GenericReasonSeed r : seed.genericReasons()) {
                if (r == null) {
                    continue;
                }
                String id = "genericReasons." + r.reasonCode();
                registerReasonCode(f, r.reasonCode(), reasonCodes, e);
                requireText(f, id, "text", r.text(), e);
                if (!spec.styleCodes().contains(r.styleCode())) {
                    e.add(f, id + ": 알 수 없는 styleCode '" + r.styleCode() + "'");
                    continue;
                }
                checkReasonCode(f, id, r.reasonCode(), spec.genericReasonCode().apply(r.styleCode()), e);
                if (!genericActual.add(r.styleCode())) {
                    e.add(f, id + ": " + r.styleCode() + "의 일반 문구가 중복됩니다.");
                }
            }
            compareCoverage(f, "스타일별 일반 문구(genericReasons)", genericActual, new LinkedHashSet<>(spec.styleCodes()), e);
        }

        // 양수 보정 근거 문구
        Set<String> positiveActual = new LinkedHashSet<>();
        if (seed.adjustmentReasons() == null) {
            e.add(f, "adjustmentReasons가 없습니다.");
        } else {
            for (RuleSeed.AdjustmentReasonSeed r : seed.adjustmentReasons()) {
                if (r == null) {
                    continue;
                }
                String id = "adjustmentReasons." + r.reasonCode();
                registerReasonCode(f, r.reasonCode(), reasonCodes, e);
                requireText(f, id, "text", r.text(), e);
                List<String> allowed = spec.adjustmentValues().get(r.factor());
                if (allowed == null || !allowed.contains(r.value())) {
                    e.add(f, id + ": 알 수 없는 보정 조건 " + r.factor() + "=" + r.value());
                    continue;
                }
                if (!spec.styleCodes().contains(r.styleCode())) {
                    e.add(f, id + ": 알 수 없는 styleCode '" + r.styleCode() + "'");
                    continue;
                }
                checkReasonCode(f, id, r.reasonCode(), spec.adjustmentReasonCode().code(r.factor(), r.value(), r.styleCode()), e);
                positiveActual.add(r.factor() + "|" + r.value() + "|" + r.styleCode());
            }
            compareCoverage(f, "양수(+) 보정의 근거 문구(adjustmentReasons)", positiveActual, positiveExpected, e);
        }
    }

    // ------------------------------------------------------------------ analysis-labels.json

    private void validateAnalysisLabels(AnalysisLabelsSeed seed, Errors e) {
        SeedFile f = SeedFile.ANALYSIS_LABELS;
        if (seed == null) {
            e.add(f, "파일 내용이 없습니다.");
            return;
        }
        // features
        if (seed.features() == null || seed.features().isEmpty()) {
            e.add(f, "features가 비어 있습니다.");
        } else {
            Set<String> keys = new HashSet<>();
            for (AnalysisLabelsSeed.FeatureLabelSeed ft : seed.features()) {
                if (ft == null) {
                    continue;
                }
                String id = "features." + ft.key();
                if (isBlank(ft.key()) || !keys.add(ft.key())) {
                    e.add(f, id + ": key가 비어 있거나 중복됩니다.");
                }
                requireText(f, id, "label", ft.label(), e);
                if (!FEATURE_UNITS.contains(ft.unit())) {
                    e.add(f, id + ": unit은 " + FEATURE_UNITS + " 중 하나여야 합니다 (현재 " + ft.unit() + ").");
                }
                if (!FEATURE_TARGETS.contains(ft.target())) {
                    e.add(f, id + ": target은 " + FEATURE_TARGETS + " 중 하나여야 합니다 (현재 " + ft.target() + ").");
                }
                if (ft.aiValueMultiplier() == null || ft.aiValueMultiplier() < 1) {
                    e.add(f, id + ": aiValueMultiplier는 1 이상이어야 합니다.");
                }
            }
            REQUIRED_FEATURE_KEYS.stream().filter(k -> !keys.contains(k))
                    .forEach(k -> e.add(f, "features: 필수 키 '" + k + "'가 없습니다."));
        }
        // Size Korea
        AnalysisLabelsSeed.SizeKoreaSeed sk = seed.sizeKorea();
        if (sk == null) {
            e.add(f, "sizeKorea가 없습니다.");
        } else {
            requireText(f, "sizeKorea", "reference", sk.reference(), e);
            requireText(f, "sizeKorea", "referenceLabel", sk.referenceLabel(), e);
            validateCodeList(f, "sizeKorea.items", sk.items(), SIZE_KOREA_ITEM_KEYS, AnalysisLabelsSeed.SizeKoreaItemSeed::key, e);
            if (sk.items() != null) {
                for (AnalysisLabelsSeed.SizeKoreaItemSeed item : sk.items()) {
                    if (item == null) {
                        continue;
                    }
                    requireText(f, "sizeKorea.items." + item.key(), "label", item.label(), e);
                    requireText(f, "sizeKorea.items." + item.key(), "sizeKoreaItem", item.sizeKoreaItem(), e);
                }
            }
        }
        // warnings
        if (seed.warnings() == null || seed.warnings().isEmpty()) {
            e.add(f, "warnings가 비어 있습니다.");
        } else {
            Set<String> codes = new HashSet<>();
            for (AnalysisLabelsSeed.WarningSeed w : seed.warnings()) {
                if (w == null) {
                    continue;
                }
                String id = "warnings." + w.code();
                if (isBlank(w.code()) || !w.code().matches("[A-Z][A-Z0-9_]*")) {
                    e.add(f, id + ": 경고 코드는 대문자 스네이크 표기여야 합니다.");
                } else if (!codes.add(w.code())) {
                    e.add(f, id + ": 경고 코드가 중복됩니다.");
                }
                if (parseSeverity(w.severity()) == null) {
                    e.add(f, id + ": severity는 RETAKE 또는 INFO여야 합니다 (현재 " + w.severity() + ").");
                }
                if (!WARNING_TARGETS.contains(w.target())) {
                    e.add(f, id + ": target은 " + WARNING_TARGETS + " 중 하나여야 합니다 (현재 " + w.target() + ").");
                }
                requireText(f, id, "message", w.message(), e);
            }
            REQUIRED_WARNING_CODES.stream().filter(c -> !codes.contains(c))
                    .forEach(c -> e.add(f, "warnings: 필수 경고 코드 '" + c + "'가 없습니다."));
        }
        AnalysisLabelsSeed.UndefinedWarningSeed uw = seed.undefinedWarning();
        if (uw == null) {
            e.add(f, "undefinedWarning(정의되지 않은 코드 기본값)이 없습니다.");
        } else {
            if (parseSeverity(uw.severity()) == null) {
                e.add(f, "undefinedWarning: severity는 RETAKE 또는 INFO여야 합니다 (현재 " + uw.severity() + ").");
            }
            requireText(f, "undefinedWarning", "message", uw.message(), e);
        }
    }

    // ------------------------------------------------------------------ helpers

    static WarningSeverity parseSeverity(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(WarningSeverity.values()).filter(s -> s.name().equals(value)).findFirst().orElse(null);
    }

    private static <T> void validateCodeList(SeedFile f, String field, List<T> items, List<String> expected,
                                             Function<T, String> codeOf, Errors e) {
        if (items == null) {
            e.add(f, field + " 배열이 없습니다.");
            return;
        }
        if (items.size() != expected.size()) {
            e.add(f, field + " 개수는 " + expected.size() + "개여야 합니다 (현재 " + items.size() + "개).");
        }
        Set<String> seen = new HashSet<>();
        for (T item : items) {
            if (item == null) {
                e.add(f, field + "에 null 항목이 있습니다.");
                continue;
            }
            String code = codeOf.apply(item);
            if (!expected.contains(code)) {
                e.add(f, field + ": 알 수 없는 Key/코드 '" + code + "' (허용: " + expected + ")");
            } else if (!seen.add(code)) {
                e.add(f, field + ": '" + code + "'가 중복됩니다.");
            }
        }
        expected.stream().filter(code -> !seen.contains(code))
                .forEach(code -> e.add(f, field + ": '" + code + "' 항목이 없습니다."));
    }

    private static void compareKeySet(SeedFile f, String field, Set<String> actual, List<String> expected, Errors e) {
        for (String k : actual) {
            if (!expected.contains(k)) {
                e.add(f, field + ": 알 수 없는 Key '" + k + "'");
            }
        }
        for (String k : expected) {
            if (!actual.contains(k)) {
                e.add(f, field + ": '" + k + "' 값이 없습니다.");
            }
        }
    }

    private static void compareCoverage(SeedFile f, String what, Set<String> actual, Set<String> expected, Errors e) {
        for (String k : expected) {
            if (!actual.contains(k)) {
                e.add(f, what + " 누락: " + k.replace('|', ' '));
            }
        }
        for (String k : actual) {
            if (!expected.contains(k)) {
                e.add(f, what + " 대상이 아닌 조합에 문구가 있습니다: " + k.replace('|', ' '));
            }
        }
    }

    private static void registerReasonCode(SeedFile f, String code, Map<String, SeedFile> reasonCodes, Errors e) {
        if (isBlank(code)) {
            e.add(f, "reasonCode가 비어 있습니다.");
            return;
        }
        SeedFile prev = reasonCodes.putIfAbsent(code, f);
        if (prev != null) {
            e.add(f, "reasonCode '" + code + "'가 중복됩니다 (" + prev.fileName() + "에도 존재).");
        }
    }

    private static void checkReasonCode(SeedFile f, String id, String actual, String expected, Errors e) {
        if (!expected.equals(actual)) {
            e.add(f, id + ": reasonCode 형식이 규칙과 다릅니다 (기대값 " + expected + ").");
        }
    }

    private static void checkSortOrder(SeedFile f, String id, Integer actual, int expected, Errors e) {
        if (!Objects.equals(actual, expected)) {
            e.add(f, id + ": sortOrder는 코드 선언 순서 " + expected + "이어야 합니다 (현재 " + actual + ").");
        }
    }

    private static void checkImageKey(SeedFile f, String id, String actual, String expected, Errors e) {
        if (!expected.equals(actual)) {
            e.add(f, id + ": imageKey는 " + expected + "이어야 합니다 (현재 " + actual + ").");
        }
    }

    private static void requireText(SeedFile f, String id, String field, String value, Errors e) {
        if (isBlank(value)) {
            e.add(f, id + ": " + field + "이(가) 비어 있습니다.");
        }
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static List<String> keys(KeyedEnum[] values) {
        return Arrays.stream(values).map(KeyedEnum::key).toList();
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static Map<String, List<String>> orderedMap(String k1, List<String> v1, String k2, List<String> v2,
                                                        String k3, List<String> v3) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    @FunctionalInterface
    interface AdjustmentReasonCode {
        String code(String factor, String value, String styleCode);
    }

    /** 헤어·패션 규칙 파일별 검증 기준. */
    record RuleSpec(
            SeedFile file,
            String domain,
            String baseFactor,
            List<String> baseKeys,
            List<String> styleCodes,
            Map<String, List<String>> adjustmentValues,
            BiFunction<String, String, String> baseReasonCode,
            Function<String, String> genericReasonCode,
            AdjustmentReasonCode adjustmentReasonCode) {
    }

    private static final class Errors {
        private final List<String> list = new ArrayList<>();

        void add(SeedFile file, String message) {
            list.add(file == null ? message : "[" + file.fileName() + "] " + message);
        }

        boolean isEmpty() {
            return list.isEmpty();
        }
    }
}
