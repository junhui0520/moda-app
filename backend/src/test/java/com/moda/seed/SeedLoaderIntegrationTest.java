package com.moda.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.FashionStyleCode;
import com.moda.common.enums.HairStyleCode;
import com.moda.common.enums.TypeCodes;
import com.moda.common.enums.WarningSeverity;
import com.moda.seed.domain.AnalysisLabel;
import com.moda.seed.domain.FashionStyle;
import com.moda.seed.domain.HairStyle;
import com.moda.seed.domain.LabelCategory;
import com.moda.seed.domain.ReasonType;
import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.RuleReason;
import com.moda.seed.domain.ScoreType;
import com.moda.seed.domain.SeedMeta;
import com.moda.seed.domain.TypeDefinition;
import com.moda.seed.repository.AnalysisLabelRepository;
import com.moda.seed.repository.BodyTypeDefRepository;
import com.moda.seed.repository.FaceShapeDefRepository;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import com.moda.seed.repository.RuleReasonRepository;
import com.moda.seed.repository.RuleScoreRepository;
import com.moda.seed.repository.SeedMetaRepository;
import com.moda.seed.repository.TypeDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 서버 기동 시 SeedLoader가 시드 5종을 검증·적재했는지 DB 기준으로 확인합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeedLoaderIntegrationTest {

    @Autowired SeedLoader seedLoader;
    @Autowired SeedMetaRepository seedMetaRepository;
    @Autowired TypeDefinitionRepository typeDefinitionRepository;
    @Autowired FaceShapeDefRepository faceShapeDefRepository;
    @Autowired BodyTypeDefRepository bodyTypeDefRepository;
    @Autowired HairStyleRepository hairStyleRepository;
    @Autowired FashionStyleRepository fashionStyleRepository;
    @Autowired RuleScoreRepository ruleScoreRepository;
    @Autowired RuleReasonRepository ruleReasonRepository;
    @Autowired AnalysisLabelRepository analysisLabelRepository;

    @Test
    @DisplayName("기동 시 적재: 유형 24 · 얼굴형 6 · 체형 4 · 헤어 5 · 패션 9")
    void loadsCatalogAndTypeCounts() {
        assertThat(typeDefinitionRepository.count()).isEqualTo(24);
        assertThat(faceShapeDefRepository.count()).isEqualTo(6);
        assertThat(bodyTypeDefRepository.count()).isEqualTo(4);
        assertThat(hairStyleRepository.count()).isEqualTo(5);
        assertThat(fashionStyleRepository.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("메타: ruleVersion v1.1-draft, headlineTemplate")
    void loadsSeedMeta() {
        assertThat(seedMetaRepository.findById(SeedMeta.RULE_VERSION)).get()
                .extracting(SeedMeta::getMetaValue).isEqualTo("v1.1-draft");
        assertThat(seedMetaRepository.findById(SeedMeta.HEADLINE_TEMPLATE)).get()
                .extracting(SeedMeta::getMetaValue).isEqualTo("당신은 {typeName} 유형입니다");
    }

    @Test
    @DisplayName("유형 24종: 모든 (체형, 얼굴형) 조합이 공식 typeCode로 1개씩 존재하고 가칭(nameConfirmed=false)")
    void everyCombinationMapsToFormulaTypeCode() {
        for (BodyType body : BodyType.values()) {
            for (FaceShape face : FaceShape.values()) {
                TypeDefinition t = typeDefinitionRepository.findByBodyTypeAndFaceShape(body, face).orElseThrow();
                assertThat(t.getTypeCode()).isEqualTo(TypeCodes.of(body, face));
                assertThat(t.getSortOrder()).isEqualTo(TypeCodes.indexOf(body, face));
                assertThat(t.isNameConfirmed()).isFalse();
                assertThat(t.getStylePoints()).hasSizeGreaterThanOrEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("TYPE-08 (작은 역삼각형 × 하트형) = V라인 프레시, stylePoints 순서 보존 (JSON 리스트 매핑)")
    void type08MatchesSpecExample() {
        TypeDefinition t = typeDefinitionRepository.findById("TYPE-08").orElseThrow();
        assertThat(t.getBodyType()).isEqualTo(BodyType.SMALL_INV_TRIANGLE);
        assertThat(t.getFaceShape()).isEqualTo(FaceShape.HEART);
        assertThat(t.getTypeName()).isEqualTo("V라인 프레시");
        assertThat(t.getDescription()).startsWith("또렷한 어깨선의 슬림한 체형과 시원한 이마에서 갸름하게 모이는 턱선이");
        assertThat(t.getStylePoints()).containsExactly(
                "테일러드 재킷·트랙 재킷처럼 어깨선이 드러나는 아이템으로 V라인 강조",
                "가르마나 내린 앞머리로 이마 라인을 자연스럽게 정돈하기");
    }

    @Test
    @DisplayName("카탈로그: 얼굴형·체형 라벨/설명, 헤어·패션 코드 순서와 itemKeywords 5개")
    void catalogContents() {
        assertThat(faceShapeDefRepository.findAllByOrderByFaceIndexAsc())
                .extracting(d -> d.getFaceShape().key())
                .containsExactly("diamond", "heart", "oval", "rectangle", "round", "long");
        assertThat(faceShapeDefRepository.findById("heart").orElseThrow().getDescription())
                .isEqualTo("이마 라인이 시원하고 턱선이 갸름하게 모여 산뜻한 인상을 주는 얼굴형입니다.");
        assertThat(bodyTypeDefRepository.findAllByOrderByBodyIndexAsc())
                .extracting(d -> d.getLabel())
                .containsExactly("표준유형", "작은 역삼각형", "큰 사각 체형", "사각 체형");

        assertThat(hairStyleRepository.findAllByOrderBySortOrderAsc())
                .extracting(HairStyle::getStyleCode)
                .containsExactly(HairStyleCode.values());
        assertThat(fashionStyleRepository.findAllByOrderBySortOrderAsc())
                .extracting(FashionStyle::getStyleCode)
                .containsExactly(FashionStyleCode.values());

        FashionStyle dandy = fashionStyleRepository.findById("DANDY").orElseThrow();
        assertThat(dandy.getItemKeywords()).containsExactly("블레이저", "셔츠", "니트 폴로", "슬랙스", "로퍼");
        assertThat(dandy.getImageKey()).isEqualTo("fashion_dandy");
        assertThat(fashionStyleRepository.findAll()).allSatisfy(f -> assertThat(f.getItemKeywords()).hasSize(5));
    }

    @Test
    @DisplayName("점수표: 헤어 기본 6×5 + 보정 9×5, 패션 기본 4×9 + 보정 11×9 (0점 칸 포함 전체)")
    void ruleScoreCounts() {
        assertThat(ruleScoreRepository.countByRuleDomainAndScoreType(RuleDomain.HAIR, ScoreType.BASE)).isEqualTo(30);
        assertThat(ruleScoreRepository.countByRuleDomainAndScoreType(RuleDomain.HAIR, ScoreType.ADJUSTMENT)).isEqualTo(45);
        assertThat(ruleScoreRepository.countByRuleDomainAndScoreType(RuleDomain.FASHION, ScoreType.BASE)).isEqualTo(36);
        assertThat(ruleScoreRepository.countByRuleDomainAndScoreType(RuleDomain.FASHION, ScoreType.ADJUSTMENT)).isEqualTo(99);

        // style-rules.md 3.1 / 3.2 / 4.1 / 4.2 대표 칸
        assertThat(score(RuleDomain.HAIR, ScoreType.BASE, "faceShape", "heart", "PARTED_STYLE")).isEqualTo(5);
        assertThat(score(RuleDomain.HAIR, ScoreType.BASE, "faceShape", "long", "UP_STYLE")).isEqualTo(1);
        assertThat(score(RuleDomain.HAIR, ScoreType.ADJUSTMENT, "hairCurl", "CURLY", "DANDY_STYLE")).isEqualTo(-2);
        assertThat(score(RuleDomain.HAIR, ScoreType.ADJUSTMENT, "hairDensity", "LOW", "UP_STYLE")).isEqualTo(-2);
        assertThat(score(RuleDomain.FASHION, ScoreType.BASE, "bodyType", "small_inv_triangle", "GORPCORE")).isEqualTo(2);
        assertThat(score(RuleDomain.FASHION, ScoreType.ADJUSTMENT, "bmi", "BMI_VERY_HIGH", "SPORTY")).isEqualTo(-1);
        assertThat(score(RuleDomain.FASHION, ScoreType.ADJUSTMENT, "age", "AGE_U22", "STREET")).isEqualTo(1);
    }

    @Test
    @DisplayName("근거 문구: 헤어 전용 20·일반 5·보정 15 / 패션 전용 16·일반 9·보정 19")
    void ruleReasonCounts() {
        assertThat(ruleReasonRepository.countByRuleDomainAndReasonType(RuleDomain.HAIR, ReasonType.BASE_SPECIFIC)).isEqualTo(20);
        assertThat(ruleReasonRepository.countByRuleDomainAndReasonType(RuleDomain.HAIR, ReasonType.BASE_GENERIC)).isEqualTo(5);
        assertThat(ruleReasonRepository.countByRuleDomainAndReasonType(RuleDomain.HAIR, ReasonType.ADJUSTMENT)).isEqualTo(15);
        assertThat(ruleReasonRepository.countByRuleDomainAndReasonType(RuleDomain.FASHION, ReasonType.BASE_SPECIFIC)).isEqualTo(16);
        assertThat(ruleReasonRepository.countByRuleDomainAndReasonType(RuleDomain.FASHION, ReasonType.BASE_GENERIC)).isEqualTo(9);
        assertThat(ruleReasonRepository.countByRuleDomainAndReasonType(RuleDomain.FASHION, ReasonType.ADJUSTMENT)).isEqualTo(19);

        RuleReason r = ruleReasonRepository.findById("ADJ_CURL_SEMI_CURLY_PARTED_STYLE").orElseThrow();
        assertThat(r.getFactor()).isEqualTo("hairCurl");
        assertThat(r.getConditionValue()).isEqualTo("SEMI_CURLY");
        assertThat(r.getStyleCode()).isEqualTo("PARTED_STYLE");
        assertThat(r.getReasonText()).isEqualTo("자연스러운 웨이브가 가르마 라인에 입체감을 더해 줍니다.");
    }

    @Test
    @DisplayName("분석 라벨: 경고 코드 16종(실시간 분석 2종 포함) + 기본값, features 8, Size Korea 항목 6 + 기준 1")
    void analysisLabels() {
        assertThat(analysisLabelRepository.countByCategory(LabelCategory.WARNING)).isEqualTo(16);
        assertThat(analysisLabelRepository.findAllByCategory(LabelCategory.WARNING))
                .extracting(AnalysisLabel::getLabelCode)
                .containsExactlyInAnyOrderElementsOf(SeedValidator.REQUIRED_WARNING_CODES);
        assertThat(analysisLabelRepository.countByCategory(LabelCategory.WARNING_FALLBACK)).isEqualTo(1);
        assertThat(analysisLabelRepository.countByCategory(LabelCategory.FEATURE)).isEqualTo(8);
        assertThat(analysisLabelRepository.countByCategory(LabelCategory.SIZE_KOREA_ITEM)).isEqualTo(6);
        assertThat(analysisLabelRepository.countByCategory(LabelCategory.SIZE_KOREA_REFERENCE)).isEqualTo(1);

        AnalysisLabel yaw = analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.WARNING, "FACE_YAW_EXCEEDED").orElseThrow();
        assertThat(yaw.getSeverity()).isEqualTo(WarningSeverity.RETAKE);
        assertThat(yaw.getTarget()).isEqualTo("FACE");
        assertThat(yaw.getDisplayText()).isEqualTo("고개를 좌우로 돌리지 말고 정면을 바라봐 주세요.");

        AnalysisLabel borderline = analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.WARNING, "BODY_BORDERLINE").orElseThrow();
        assertThat(borderline.getSeverity()).isEqualTo(WarningSeverity.INFO);

        AnalysisLabel notEnough = analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.WARNING, "LIVE_NOT_ENOUGH_FRAMES").orElseThrow();
        assertThat(notEnough.getSeverity()).isEqualTo(WarningSeverity.RETAKE);
        assertThat(notEnough.getTarget()).isEqualTo("COMMON");
        assertThat(notEnough.getDisplayText()).isEqualTo("측정이 충분히 되지 않았어요. 안내에 맞춰 다시 측정해 주세요.");
        AnalysisLabel legs = analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.WARNING, "BODY_LEGS_CLOSED").orElseThrow();
        assertThat(legs.getSeverity()).isEqualTo(WarningSeverity.INFO);
        assertThat(legs.getTarget()).isEqualTo("BODY");

        AnalysisLabel fallback = analysisLabelRepository
                .findByCategoryAndLabelCode(LabelCategory.WARNING_FALLBACK, AnalysisLabel.UNDEFINED_WARNING_CODE).orElseThrow();
        assertThat(fallback.getSeverity()).isEqualTo(WarningSeverity.INFO);
        assertThat(fallback.getDisplayText()).isEqualTo("촬영 상태를 한 번 더 확인해 주세요.");

        AnalysisLabel shoulderDev = analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.FEATURE, "shoulder_dev").orElseThrow();
        assertThat(shoulderDev.getUnit()).isEqualTo("percent");
        assertThat(shoulderDev.getAiValueMultiplier()).isEqualTo(100);

        AnalysisLabel crotch = analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.SIZE_KOREA_ITEM, "crotch_height").orElseThrow();
        assertThat(crotch.getDisplayText()).isEqualTo("다리 길이(샅높이)");
        assertThat(crotch.getSourceName()).isEqualTo("(110) 샅높이");

        assertThat(analysisLabelRepository.findByCategoryAndLabelCode(LabelCategory.SIZE_KOREA_REFERENCE, "SIZE_KOREA_20S_MALE"))
                .get().extracting(AnalysisLabel::getDisplayText).isEqualTo("Size Korea 20대 남성 평균");
    }

    @Test
    @DisplayName("재적재(재기동)는 멱등: 신규 0건, 개수 불변, DB에서 바뀐 텍스트는 시드 값으로 복구")
    void reloadIsIdempotentUpsert() {
        long typesBefore = typeDefinitionRepository.count();
        long scoresBefore = ruleScoreRepository.count();
        long labelsBefore = analysisLabelRepository.count();

        // DB 텍스트를 임의로 변경 → 재적재 시 시드 값으로 갱신되어야 함
        HairStyle up = hairStyleRepository.findById("UP_STYLE").orElseThrow();
        up.update("임시 이름", up.getDescription(), up.getImageKey(), up.getSortOrder());
        hairStyleRepository.save(up);
        assertThat(hairStyleRepository.findById("UP_STYLE").orElseThrow().getName()).isEqualTo("임시 이름");

        SeedLoadResult result = seedLoader.load().orElseThrow();

        assertThat(result.totalInserted()).isZero();
        assertThat(result.totalUpdated()).isEqualTo(totalSeedRows());
        assertThat(result.table(SeedUpsertService.T_TYPE_DEFINITION).updated()).isEqualTo(24);
        assertThat(typeDefinitionRepository.count()).isEqualTo(typesBefore);
        assertThat(ruleScoreRepository.count()).isEqualTo(scoresBefore);
        assertThat(analysisLabelRepository.count()).isEqualTo(labelsBefore);
        assertThat(hairStyleRepository.findById("UP_STYLE").orElseThrow().getName()).isEqualTo("깐머리 스타일");
    }

    private long totalSeedRows() {
        return seedMetaRepository.count() + typeDefinitionRepository.count() + faceShapeDefRepository.count()
                + bodyTypeDefRepository.count() + hairStyleRepository.count() + fashionStyleRepository.count()
                + ruleScoreRepository.count() + ruleReasonRepository.count() + analysisLabelRepository.count();
    }

    private int score(RuleDomain domain, ScoreType type, String factor, String value, String style) {
        return ruleScoreRepository.findByRuleDomainAndScoreTypeAndFactorAndConditionValueAndStyleCode(domain, type, factor, value, style)
                .orElseThrow().getScore();
    }
}
