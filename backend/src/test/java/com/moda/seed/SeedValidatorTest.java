package com.moda.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moda.seed.dto.AnalysisLabelsSeed;
import com.moda.seed.dto.CatalogSeed;
import com.moda.seed.dto.RuleSeed;
import com.moda.seed.dto.SeedBundle;
import com.moda.seed.dto.TypeDefinitionsSeed;
import com.moda.seed.dto.TypeDefinitionsSeed.TypeSeed;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * 실제 시드 JSON을 읽은 뒤 일부를 망가뜨려, 검증기가 개수·조합·Key·문구 누락을 모두 잡아내는지 확인합니다.
 */
class SeedValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SeedValidator validator = new SeedValidator();
    private SeedBundle valid;

    @BeforeEach
    void setUp() {
        valid = new SeedReader(MAPPER, new DefaultResourceLoader(), "classpath:seed/").read();
    }

    @Test
    @DisplayName("실제 시드 5종은 검증을 통과한다")
    void realSeedPasses() {
        assertThatCode(() -> validator.validate(valid)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시드 파일 간 ruleVersion이 다르면 실패")
    void ruleVersionMismatch() {
        CatalogSeed c = valid.catalog();
        SeedBundle broken = withCatalog(new CatalogSeed("v9.9", c.faceShapes(), c.bodyTypes(), c.hairStyles(), c.fashionStyles()));
        assertThat(errorsOf(broken)).anyMatch(m -> m.contains("ruleVersion이 다릅니다"));
    }

    @Nested
    @DisplayName("type-definitions.json")
    class TypeDefinitions {

        @Test
        @DisplayName("유형이 23개면 개수 오류 + 누락 조합 보고")
        void typeCount() {
            List<TypeSeed> types = new ArrayList<>(valid.typeDefinitions().types());
            types.remove(types.size() - 1);
            assertThat(errorsOf(withTypes(types)))
                    .anyMatch(m -> m.contains("유형 개수는 24개"))
                    .anyMatch(m -> m.contains("(rectangle, long)") && m.contains("TYPE-24"));
        }

        @Test
        @DisplayName("(체형, 얼굴형) 조합 중복 + typeCode 공식 불일치")
        void duplicateCombination() {
            List<TypeSeed> types = new ArrayList<>(valid.typeDefinitions().types());
            TypeSeed t24 = types.get(23);
            types.set(23, new TypeSeed(t24.typeCode(), "rectangle", "diamond", t24.typeName(), t24.nameConfirmed(),
                    t24.description(), t24.stylePoints(), t24.sortOrder()));
            assertThat(errorsOf(withTypes(types)))
                    .anyMatch(m -> m.contains("(rectangle, diamond)이 중복"))
                    .anyMatch(m -> m.contains("TYPE-24") && m.contains("TYPE-19"));
        }

        @Test
        @DisplayName("알 수 없는 얼굴형 Key")
        void unknownFaceKey() {
            List<TypeSeed> types = new ArrayList<>(valid.typeDefinitions().types());
            TypeSeed t = types.get(0);
            types.set(0, new TypeSeed(t.typeCode(), t.bodyType(), "triangle", t.typeName(), t.nameConfirmed(),
                    t.description(), t.stylePoints(), t.sortOrder()));
            assertThat(errorsOf(withTypes(types))).anyMatch(m -> m.contains("알 수 없는 faceShape Key 'triangle'"));
        }

        @Test
        @DisplayName("유형명 20자 초과, 스타일 포인트 1개, headlineTemplate 자리표시자 누락")
        void fieldRules() {
            List<TypeSeed> types = new ArrayList<>(valid.typeDefinitions().types());
            TypeSeed t = types.get(1);
            types.set(1, new TypeSeed(t.typeCode(), t.bodyType(), t.faceShape(), "가".repeat(21), t.nameConfirmed(),
                    t.description(), List.of("하나뿐"), t.sortOrder()));
            SeedBundle broken = withTypeDefinitions(new TypeDefinitionsSeed("v1.1-draft", "당신은 유형입니다", types));
            assertThat(errorsOf(broken))
                    .anyMatch(m -> m.contains("TYPE-02: typeName은 1~20자"))
                    .anyMatch(m -> m.contains("TYPE-02: stylePoints는 2개 이상"))
                    .anyMatch(m -> m.contains("headlineTemplate"));
        }
    }

    @Nested
    @DisplayName("catalog.json")
    class Catalog {

        @Test
        @DisplayName("헤어 4종, 패션 itemKeywords 4개, 잘못된 imageKey")
        void catalogRules() {
            CatalogSeed c = valid.catalog();
            List<CatalogSeed.HairStyleSeed> hair = new ArrayList<>(c.hairStyles());
            hair.remove(4); // SHADOW_STYLE
            List<CatalogSeed.FashionStyleSeed> fashion = new ArrayList<>(c.fashionStyles());
            CatalogSeed.FashionStyleSeed dandy = fashion.get(1);
            fashion.set(1, new CatalogSeed.FashionStyleSeed(dandy.code(), dandy.name(), dandy.description(),
                    dandy.itemKeywords().subList(0, 4), "fashion_dandy_v2", dandy.sortOrder()));
            SeedBundle broken = withCatalog(new CatalogSeed(c.ruleVersion(), c.faceShapes(), c.bodyTypes(), hair, fashion));
            assertThat(errorsOf(broken))
                    .anyMatch(m -> m.contains("hairStyles 개수는 5개"))
                    .anyMatch(m -> m.contains("'SHADOW_STYLE' 항목이 없습니다"))
                    .anyMatch(m -> m.contains("fashionStyles.DANDY: itemKeywords는 5개"))
                    .anyMatch(m -> m.contains("fashionStyles.DANDY: imageKey는 fashion_dandy"));
        }

        @Test
        @DisplayName("얼굴형 faceIndex가 선언 순서와 다르면 실패")
        void faceIndex() {
            CatalogSeed c = valid.catalog();
            List<CatalogSeed.FaceShapeSeed> faces = new ArrayList<>(c.faceShapes());
            CatalogSeed.FaceShapeSeed heart = faces.get(1);
            faces.set(1, new CatalogSeed.FaceShapeSeed(heart.key(), heart.label(), 5, heart.description()));
            SeedBundle broken = withCatalog(new CatalogSeed(c.ruleVersion(), faces, c.bodyTypes(), c.hairStyles(), c.fashionStyles()));
            assertThat(errorsOf(broken)).anyMatch(m -> m.contains("faceShapes.heart: faceIndex는 1"));
        }
    }

    @Nested
    @DisplayName("hair-rules.json / fashion-rules.json")
    class Rules {

        @Test
        @DisplayName("기본 점수를 4점으로 올렸는데 전용 문구가 없으면 실패 (3.3 규칙)")
        void specificReasonRequiredForBaseScore4() {
            RuleSeed h = valid.hairRules();
            Map<String, Map<String, Integer>> base = deepCopy(h.baseScores());
            base.get("heart").put("UP_STYLE", 4);
            RuleSeed broken = new RuleSeed(h.ruleVersion(), h.domain(), h.baseFactor(), h.styleOrder(), h.baseScoreRange(),
                    base, h.baseRationale(), h.adjustments(), h.baseReasons(), h.genericReasons(), h.adjustmentReasons());
            assertThat(errorsOf(withHair(broken))).anyMatch(m -> m.contains("전용 문구(baseReasons) 누락: heart UP_STYLE"));
        }

        @Test
        @DisplayName("기본 점수 범위(0~5) 초과")
        void baseScoreRange() {
            RuleSeed f = valid.fashionRules();
            Map<String, Map<String, Integer>> base = deepCopy(f.baseScores());
            base.get("standard").put("VINTAGE", 6);
            RuleSeed broken = new RuleSeed(f.ruleVersion(), f.domain(), f.baseFactor(), f.styleOrder(), f.baseScoreRange(),
                    base, f.baseRationale(), f.adjustments(), f.baseReasons(), f.genericReasons(), f.adjustmentReasons());
            assertThat(errorsOf(withFashion(broken))).anyMatch(m -> m.contains("baseScores.standard.VINTAGE") && m.contains("0~5"));
        }

        @Test
        @DisplayName("양수 보정의 근거 문구가 빠지면 실패 (4.4 규칙)")
        void positiveAdjustmentNeedsReason() {
            RuleSeed f = valid.fashionRules();
            List<RuleSeed.AdjustmentReasonSeed> reasons = new ArrayList<>(f.adjustmentReasons());
            reasons.removeIf(r -> r.reasonCode().equals("ADJ_AGE_U22_STREET"));
            RuleSeed broken = new RuleSeed(f.ruleVersion(), f.domain(), f.baseFactor(), f.styleOrder(), f.baseScoreRange(),
                    f.baseScores(), f.baseRationale(), f.adjustments(), f.baseReasons(), f.genericReasons(), reasons);
            assertThat(errorsOf(withFashion(broken))).anyMatch(m -> m.contains("adjustmentReasons) 누락: age AGE_U22 STREET"));
        }

        @Test
        @DisplayName("보정 행 누락 + reasonCode 형식 오류")
        void missingAdjustmentRowAndBadCode() {
            RuleSeed f = valid.fashionRules();
            List<RuleSeed.AdjustmentRowSeed> rows = new ArrayList<>(f.adjustments());
            rows.removeIf(r -> r.value().equals("HEIGHT_MID"));
            List<RuleSeed.GenericReasonSeed> generic = new ArrayList<>(f.genericReasons());
            RuleSeed.GenericReasonSeed g = generic.get(0);
            generic.set(0, new RuleSeed.GenericReasonSeed("FASHION_GEN_CASUAL", g.styleCode(), g.text()));
            RuleSeed broken = new RuleSeed(f.ruleVersion(), f.domain(), f.baseFactor(), f.styleOrder(), f.baseScoreRange(),
                    f.baseScores(), f.baseRationale(), rows, f.baseReasons(), generic, f.adjustmentReasons());
            assertThat(errorsOf(withFashion(broken)))
                    .anyMatch(m -> m.contains("height=HEIGHT_MID 보정 행이 없습니다"))
                    .anyMatch(m -> m.contains("FASHION_GEN_CASUAL") && m.contains("기대값 FASHION_GENERIC_CASUAL"));
        }

        @Test
        @DisplayName("알 수 없는 모질 값")
        void unknownAdjustmentValue() {
            RuleSeed h = valid.hairRules();
            List<RuleSeed.AdjustmentRowSeed> rows = new ArrayList<>(h.adjustments());
            RuleSeed.AdjustmentRowSeed r0 = rows.get(0);
            rows.set(0, new RuleSeed.AdjustmentRowSeed(r0.factor(), "ULTRA_THIN", r0.deltas(), r0.note()));
            RuleSeed broken = new RuleSeed(h.ruleVersion(), h.domain(), h.baseFactor(), h.styleOrder(), h.baseScoreRange(),
                    h.baseScores(), h.baseRationale(), rows, h.baseReasons(), h.genericReasons(), h.adjustmentReasons());
            assertThat(errorsOf(withHair(broken)))
                    .anyMatch(m -> m.contains("알 수 없는 값 'ULTRA_THIN'"))
                    .anyMatch(m -> m.contains("hairThickness=THIN 보정 행이 없습니다"));
        }
    }

    @Nested
    @DisplayName("analysis-labels.json")
    class AnalysisLabels {

        @Test
        @DisplayName("필수 경고 코드 누락, 잘못된 severity, Size Korea 항목 누락")
        void warningAndSizeKoreaRules() {
            AnalysisLabelsSeed a = valid.analysisLabels();
            List<AnalysisLabelsSeed.WarningSeed> warnings = new ArrayList<>(a.warnings());
            warnings.removeIf(w -> w.code().equals("SMILING"));
            AnalysisLabelsSeed.WarningSeed w0 = warnings.get(0);
            warnings.set(0, new AnalysisLabelsSeed.WarningSeed(w0.code(), w0.target(), "WARN", w0.criteria(), w0.message()));
            List<AnalysisLabelsSeed.SizeKoreaItemSeed> items = new ArrayList<>(a.sizeKorea().items());
            items.remove(5);
            AnalysisLabelsSeed broken = new AnalysisLabelsSeed(a.ruleVersion(), a.features(),
                    new AnalysisLabelsSeed.SizeKoreaSeed(a.sizeKorea().reference(), a.sizeKorea().referenceLabel(), items),
                    warnings, a.undefinedWarning());
            assertThat(errorsOf(withLabels(broken)))
                    .anyMatch(m -> m.contains("필수 경고 코드 'SMILING'"))
                    .anyMatch(m -> m.contains("FACE_NOT_DETECTED: severity는 RETAKE 또는 INFO"))
                    .anyMatch(m -> m.contains("'arm_length' 항목이 없습니다"));
        }
    }

    @Nested
    @DisplayName("SeedReader (파일 단위)")
    class Reader {

        @TempDir
        Path dir;

        @Test
        @DisplayName("정의되지 않은 필드(오타)가 있으면 JSON 파싱 단계에서 실패")
        void unknownPropertyFails() throws IOException {
            copySeedFiles(dir);
            ObjectNode root = (ObjectNode) MAPPER.readTree(dir.resolve("catalog.json").toFile());
            ((ObjectNode) root.withArray("hairStyles").get(0)).put("imagekey", "typo");
            MAPPER.writeValue(dir.resolve("catalog.json").toFile(), root);

            SeedReader reader = new SeedReader(MAPPER, new DefaultResourceLoader(), dir.toUri().toString());
            assertThatThrownBy(reader::read)
                    .isInstanceOf(SeedValidationException.class)
                    .hasMessageContaining("[catalog.json] JSON 파싱 실패");
        }

        @Test
        @DisplayName("시드 파일이 없으면 실패")
        void missingFileFails() throws IOException {
            copySeedFiles(dir);
            Files.delete(dir.resolve("analysis-labels.json"));
            SeedReader reader = new SeedReader(MAPPER, new DefaultResourceLoader(), dir.toUri().toString());
            assertThatThrownBy(reader::read)
                    .isInstanceOf(SeedValidationException.class)
                    .hasMessageContaining("[analysis-labels.json] 시드 파일이 없습니다");
        }

        @Test
        @DisplayName("파일에서 패션스타일 1개를 지우면 검증 실패")
        void removedFashionStyleFailsValidation() throws IOException {
            copySeedFiles(dir);
            ObjectNode root = (ObjectNode) MAPPER.readTree(dir.resolve("catalog.json").toFile());
            ((ArrayNode) root.get("fashionStyles")).remove(8);
            MAPPER.writeValue(dir.resolve("catalog.json").toFile(), root);

            SeedBundle bundle = new SeedReader(MAPPER, new DefaultResourceLoader(), dir.toUri().toString()).read();
            assertThat(errorsOf(bundle))
                    .anyMatch(m -> m.contains("fashionStyles 개수는 9개여야 합니다 (현재 8개)"))
                    .anyMatch(m -> m.contains("'VINTAGE' 항목이 없습니다"));
        }
    }

    // ------------------------------------------------------------------ helpers

    static void copySeedFiles(Path dir) throws IOException {
        for (SeedFile f : SeedFile.values()) {
            try (InputStream in = SeedValidatorTest.class.getResourceAsStream("/seed/" + f.fileName())) {
                assertThat(in).as(f.fileName()).isNotNull();
                Files.copy(in, dir.resolve(f.fileName()));
            }
        }
    }

    private List<String> errorsOf(SeedBundle bundle) {
        try {
            validator.validate(bundle);
        } catch (SeedValidationException e) {
            return e.getErrors();
        }
        throw new AssertionError("검증 실패를 기대했지만 통과했습니다.");
    }

    private static Map<String, Map<String, Integer>> deepCopy(Map<String, Map<String, Integer>> src) {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        src.forEach((k, v) -> copy.put(k, new LinkedHashMap<>(v)));
        return copy;
    }

    private SeedBundle withTypes(List<TypeSeed> types) {
        TypeDefinitionsSeed t = valid.typeDefinitions();
        return withTypeDefinitions(new TypeDefinitionsSeed(t.ruleVersion(), t.headlineTemplate(), types));
    }

    private SeedBundle withTypeDefinitions(TypeDefinitionsSeed t) {
        return new SeedBundle(t, valid.catalog(), valid.hairRules(), valid.fashionRules(), valid.analysisLabels());
    }

    private SeedBundle withCatalog(CatalogSeed c) {
        return new SeedBundle(valid.typeDefinitions(), c, valid.hairRules(), valid.fashionRules(), valid.analysisLabels());
    }

    private SeedBundle withHair(RuleSeed h) {
        return new SeedBundle(valid.typeDefinitions(), valid.catalog(), h, valid.fashionRules(), valid.analysisLabels());
    }

    private SeedBundle withFashion(RuleSeed f) {
        return new SeedBundle(valid.typeDefinitions(), valid.catalog(), valid.hairRules(), f, valid.analysisLabels());
    }

    private SeedBundle withLabels(AnalysisLabelsSeed a) {
        return new SeedBundle(valid.typeDefinitions(), valid.catalog(), valid.hairRules(), valid.fashionRules(), a);
    }
}
