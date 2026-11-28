package com.moda.seed;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.seed.domain.AnalysisLabel;
import com.moda.seed.domain.BodyTypeDef;
import com.moda.seed.domain.FaceShapeDef;
import com.moda.seed.domain.FashionStyle;
import com.moda.seed.domain.HairStyle;
import com.moda.seed.domain.LabelCategory;
import com.moda.seed.domain.ReasonType;
import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.RuleReason;
import com.moda.seed.domain.RuleScore;
import com.moda.seed.domain.ScoreType;
import com.moda.seed.domain.SeedMeta;
import com.moda.seed.domain.TypeDefinition;
import com.moda.seed.dto.AnalysisLabelsSeed;
import com.moda.seed.dto.CatalogSeed;
import com.moda.seed.dto.RuleSeed;
import com.moda.seed.dto.SeedBundle;
import com.moda.seed.dto.TypeDefinitionsSeed;
import com.moda.seed.repository.AnalysisLabelRepository;
import com.moda.seed.repository.BodyTypeDefRepository;
import com.moda.seed.repository.FaceShapeDefRepository;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import com.moda.seed.repository.RuleReasonRepository;
import com.moda.seed.repository.RuleScoreRepository;
import com.moda.seed.repository.SeedMetaRepository;
import com.moda.seed.repository.TypeDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검증을 통과한 시드를 <b>코드(자연 키) 기준 upsert</b>합니다 (style-rules.md 5.3 적재 규칙 1).
 * 코드가 같으면 텍스트·점수만 갱신되므로 세션 데이터에는 영향이 없습니다. 하나의 트랜잭션으로 처리합니다.
 */
@Service
public class SeedUpsertService {

    static final String T_SEED_META = "seed_meta";
    static final String T_TYPE_DEFINITION = "type_definition";
    static final String T_FACE_SHAPE_DEF = "face_shape_def";
    static final String T_BODY_TYPE_DEF = "body_type_def";
    static final String T_HAIR_STYLE = "hair_style";
    static final String T_FASHION_STYLE = "fashion_style";
    static final String T_RULE_SCORE = "rule_score";
    static final String T_RULE_REASON = "rule_reason";
    static final String T_ANALYSIS_LABEL = "analysis_label";

    private final SeedMetaRepository seedMetaRepository;
    private final TypeDefinitionRepository typeDefinitionRepository;
    private final FaceShapeDefRepository faceShapeDefRepository;
    private final BodyTypeDefRepository bodyTypeDefRepository;
    private final HairStyleRepository hairStyleRepository;
    private final FashionStyleRepository fashionStyleRepository;
    private final RuleScoreRepository ruleScoreRepository;
    private final RuleReasonRepository ruleReasonRepository;
    private final AnalysisLabelRepository analysisLabelRepository;

    public SeedUpsertService(SeedMetaRepository seedMetaRepository,
                             TypeDefinitionRepository typeDefinitionRepository,
                             FaceShapeDefRepository faceShapeDefRepository,
                             BodyTypeDefRepository bodyTypeDefRepository,
                             HairStyleRepository hairStyleRepository,
                             FashionStyleRepository fashionStyleRepository,
                             RuleScoreRepository ruleScoreRepository,
                             RuleReasonRepository ruleReasonRepository,
                             AnalysisLabelRepository analysisLabelRepository) {
        this.seedMetaRepository = seedMetaRepository;
        this.typeDefinitionRepository = typeDefinitionRepository;
        this.faceShapeDefRepository = faceShapeDefRepository;
        this.bodyTypeDefRepository = bodyTypeDefRepository;
        this.hairStyleRepository = hairStyleRepository;
        this.fashionStyleRepository = fashionStyleRepository;
        this.ruleScoreRepository = ruleScoreRepository;
        this.ruleReasonRepository = ruleReasonRepository;
        this.analysisLabelRepository = analysisLabelRepository;
    }

    @Transactional
    public SeedLoadResult upsert(SeedBundle bundle) {
        TypeDefinitionsSeed types = bundle.typeDefinitions();
        SeedLoadResult.Builder result = new SeedLoadResult.Builder(types.ruleVersion());

        upsertMeta(types, result);
        upsertTypes(types, result);
        upsertCatalog(bundle.catalog(), result);
        upsertRules(bundle.hairRules(), RuleDomain.HAIR, result);
        upsertRules(bundle.fashionRules(), RuleDomain.FASHION, result);
        upsertAnalysisLabels(bundle.analysisLabels(), result);

        return result.build();
    }

    // ------------------------------------------------------------------ meta / types / catalog

    private void upsertMeta(TypeDefinitionsSeed seed, SeedLoadResult.Builder result) {
        List<Map.Entry<String, String>> metas = List.of(
                Map.entry(SeedMeta.RULE_VERSION, seed.ruleVersion()),
                Map.entry(SeedMeta.HEADLINE_TEMPLATE, seed.headlineTemplate()));
        upsert(T_SEED_META, seedMetaRepository, SeedMeta::getMetaKey, metas, Map.Entry::getKey,
                key -> new SeedMeta(key, null), (entity, s) -> entity.update(s.getValue()), result);
    }

    private void upsertTypes(TypeDefinitionsSeed seed, SeedLoadResult.Builder result) {
        upsert(T_TYPE_DEFINITION, typeDefinitionRepository, TypeDefinition::getTypeCode, seed.types(),
                TypeDefinitionsSeed.TypeSeed::typeCode, TypeDefinition::new,
                (entity, s) -> entity.update(BodyType.fromKey(s.bodyType()), FaceShape.fromKey(s.faceShape()), s.typeName(),
                        s.nameConfirmed(), s.description(), s.stylePoints(), s.sortOrder()),
                result);
    }

    private void upsertCatalog(CatalogSeed seed, SeedLoadResult.Builder result) {
        upsert(T_FACE_SHAPE_DEF, faceShapeDefRepository, FaceShapeDef::getFaceKey, seed.faceShapes(),
                CatalogSeed.FaceShapeSeed::key, FaceShapeDef::new,
                (entity, s) -> entity.update(s.label(), s.faceIndex(), s.description()), result);
        upsert(T_BODY_TYPE_DEF, bodyTypeDefRepository, BodyTypeDef::getBodyKey, seed.bodyTypes(),
                CatalogSeed.BodyTypeSeed::key, BodyTypeDef::new,
                (entity, s) -> entity.update(s.label(), s.bodyIndex(), s.description(), s.aiInternalLabel(), s.ruleSummary()),
                result);
        upsert(T_HAIR_STYLE, hairStyleRepository, HairStyle::getCode, seed.hairStyles(),
                CatalogSeed.HairStyleSeed::code, HairStyle::new,
                (entity, s) -> entity.update(s.name(), s.description(), s.imageKey(), s.sortOrder()), result);
        upsert(T_FASHION_STYLE, fashionStyleRepository, FashionStyle::getCode, seed.fashionStyles(),
                CatalogSeed.FashionStyleSeed::code, FashionStyle::new,
                (entity, s) -> entity.update(s.name(), s.description(), s.itemKeywords(), s.imageKey(), s.sortOrder()),
                result);
    }

    // ------------------------------------------------------------------ rules

    private record ScoreCell(RuleScore.NaturalKey key, int score) {
    }

    private record ReasonRow(String code, ReasonType type, String factor, String conditionValue, String styleCode, String text) {
    }

    private void upsertRules(RuleSeed seed, RuleDomain domain, SeedLoadResult.Builder result) {
        List<ScoreCell> cells = new ArrayList<>();
        seed.baseScores().forEach((baseKey, row) -> row.forEach((style, score) -> cells.add(new ScoreCell(
                new RuleScore.NaturalKey(domain, ScoreType.BASE, seed.baseFactor(), baseKey, style), score))));
        for (RuleSeed.AdjustmentRowSeed adj : seed.adjustments()) {
            adj.deltas().forEach((style, delta) -> cells.add(new ScoreCell(
                    new RuleScore.NaturalKey(domain, ScoreType.ADJUSTMENT, adj.factor(), adj.value(), style), delta)));
        }
        Map<RuleScore.NaturalKey, RuleScore> existingScores = ruleScoreRepository.findAllByRuleDomain(domain).stream()
                .collect(Collectors.toMap(RuleScore::naturalKey, Function.identity()));
        List<RuleScore> newScores = new ArrayList<>();
        for (ScoreCell cell : cells) {
            RuleScore entity = existingScores.get(cell.key());
            if (entity == null) {
                RuleScore.NaturalKey k = cell.key();
                newScores.add(new RuleScore(k.ruleDomain(), k.scoreType(), k.factor(), k.conditionValue(), k.styleCode(), cell.score()));
                result.add(T_RULE_SCORE, true);
            } else {
                entity.updateScore(cell.score());
                result.add(T_RULE_SCORE, false);
            }
        }
        ruleScoreRepository.saveAll(newScores);

        List<ReasonRow> reasons = new ArrayList<>();
        seed.baseReasons().forEach(r -> reasons.add(new ReasonRow(
                r.reasonCode(), ReasonType.BASE_SPECIFIC, seed.baseFactor(), r.baseKey(), r.styleCode(), r.text())));
        seed.genericReasons().forEach(r -> reasons.add(new ReasonRow(
                r.reasonCode(), ReasonType.BASE_GENERIC, null, null, r.styleCode(), r.text())));
        seed.adjustmentReasons().forEach(r -> reasons.add(new ReasonRow(
                r.reasonCode(), ReasonType.ADJUSTMENT, r.factor(), r.value(), r.styleCode(), r.text())));
        upsert(T_RULE_REASON, ruleReasonRepository, RuleReason::getReasonCode, reasons, ReasonRow::code, RuleReason::new,
                (entity, r) -> entity.update(domain, r.type(), r.factor(), r.conditionValue(), r.styleCode(), r.text()), result);
    }

    // ------------------------------------------------------------------ analysis labels

    private record LabelRow(LabelCategory category, String code, String displayText, String unit, String description,
                            String severity, String target, String sourceName, String criteria, Integer aiValueMultiplier,
                            String note) {
        AnalysisLabel.NaturalKey key() {
            return new AnalysisLabel.NaturalKey(category, code);
        }
    }

    private void upsertAnalysisLabels(AnalysisLabelsSeed seed, SeedLoadResult.Builder result) {
        List<LabelRow> rows = new ArrayList<>();
        for (AnalysisLabelsSeed.FeatureLabelSeed f : seed.features()) {
            rows.add(new LabelRow(LabelCategory.FEATURE, f.key(), f.label(), f.unit(), f.description(), null, f.target(),
                    null, null, f.aiValueMultiplier(), f.note()));
        }
        AnalysisLabelsSeed.SizeKoreaSeed sk = seed.sizeKorea();
        rows.add(new LabelRow(LabelCategory.SIZE_KOREA_REFERENCE, sk.reference(), sk.referenceLabel(), null, null, null,
                "BODY", null, null, null, null));
        for (AnalysisLabelsSeed.SizeKoreaItemSeed item : sk.items()) {
            rows.add(new LabelRow(LabelCategory.SIZE_KOREA_ITEM, item.key(), item.label(), null, null, null, "BODY",
                    item.sizeKoreaItem(), null, null, null));
        }
        for (AnalysisLabelsSeed.WarningSeed w : seed.warnings()) {
            rows.add(new LabelRow(LabelCategory.WARNING, w.code(), w.message(), null, null, w.severity(), w.target(),
                    null, w.criteria(), null, null));
        }
        AnalysisLabelsSeed.UndefinedWarningSeed uw = seed.undefinedWarning();
        rows.add(new LabelRow(LabelCategory.WARNING_FALLBACK, AnalysisLabel.UNDEFINED_WARNING_CODE, uw.message(), null, null,
                uw.severity(), "COMMON", null, null, null, null));

        Map<AnalysisLabel.NaturalKey, AnalysisLabel> existing = analysisLabelRepository.findAll().stream()
                .collect(Collectors.toMap(AnalysisLabel::naturalKey, Function.identity()));
        List<AnalysisLabel> newLabels = new ArrayList<>();
        for (LabelRow row : rows) {
            AnalysisLabel entity = existing.get(row.key());
            boolean isNew = entity == null;
            if (isNew) {
                entity = new AnalysisLabel(row.category(), row.code());
                newLabels.add(entity);
            }
            entity.update(row.displayText(), row.unit(), row.description(), SeedValidator.parseSeverity(row.severity()),
                    row.target(), row.sourceName(), row.criteria(), row.aiValueMultiplier(), row.note());
            result.add(T_ANALYSIS_LABEL, isNew);
        }
        analysisLabelRepository.saveAll(newLabels);
    }

    // ------------------------------------------------------------------ generic upsert (문자열 코드 PK)

    private <E, S> void upsert(String table, JpaRepository<E, String> repository, Function<E, String> idOfEntity,
                               List<S> seeds, Function<S, String> idOfSeed, Function<String, E> factory,
                               BiConsumer<E, S> updater, SeedLoadResult.Builder result) {
        Map<String, E> existing = repository.findAll().stream()
                .collect(Collectors.toMap(idOfEntity, Function.identity()));
        List<E> newEntities = new ArrayList<>();
        for (S seed : seeds) {
            String id = idOfSeed.apply(seed);
            E entity = existing.get(id);
            boolean isNew = entity == null;
            if (isNew) {
                entity = factory.apply(id);
                newEntities.add(entity);
            }
            updater.accept(entity, seed);
            result.add(table, isNew);
        }
        repository.saveAll(newEntities);
    }
}
