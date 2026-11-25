package com.moda.seed.catalog;

import com.moda.common.dto.CodeLabel;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.FashionStyleCode;
import com.moda.common.enums.HairStyleCode;
import com.moda.common.enums.WarningSeverity;
import com.moda.seed.SeedLoadedEvent;
import com.moda.seed.domain.AnalysisLabel;
import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.RuleScore;
import com.moda.seed.domain.ScoreType;
import com.moda.seed.domain.SeedMeta;
import com.moda.seed.repository.AnalysisLabelRepository;
import com.moda.seed.repository.BodyTypeDefRepository;
import com.moda.seed.repository.FaceShapeDefRepository;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import com.moda.seed.repository.RuleReasonRepository;
import com.moda.seed.repository.RuleScoreRepository;
import com.moda.seed.repository.SeedMetaRepository;
import com.moda.seed.repository.TypeDefinitionRepository;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 시드 테이블의 읽기 전용 캐시. 추천 엔진·분석 라벨 변환이 요청마다 DB를 조회하지 않도록 메모리에 보관합니다.
 * <p>최초 사용 시 적재하고, 시드가 다시 upsert되면({@link SeedLoadedEvent}) 무효화됩니다.
 * 화면 표시 텍스트는 모두 이 캐시(=시드)에서 읽으며 코드에 하드코딩하지 않습니다 (R-07).
 */
@Component
public class SeedCatalog {

    private final SeedMetaRepository seedMetaRepository;
    private final TypeDefinitionRepository typeDefinitionRepository;
    private final FaceShapeDefRepository faceShapeDefRepository;
    private final BodyTypeDefRepository bodyTypeDefRepository;
    private final HairStyleRepository hairStyleRepository;
    private final FashionStyleRepository fashionStyleRepository;
    private final RuleScoreRepository ruleScoreRepository;
    private final RuleReasonRepository ruleReasonRepository;
    private final AnalysisLabelRepository analysisLabelRepository;

    private volatile Snapshot snapshot;

    public SeedCatalog(SeedMetaRepository seedMetaRepository, TypeDefinitionRepository typeDefinitionRepository,
                       FaceShapeDefRepository faceShapeDefRepository, BodyTypeDefRepository bodyTypeDefRepository,
                       HairStyleRepository hairStyleRepository, FashionStyleRepository fashionStyleRepository,
                       RuleScoreRepository ruleScoreRepository, RuleReasonRepository ruleReasonRepository,
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

    // ------------------------------------------------------------------ records

    public record TypeDef(String typeCode, String typeName, boolean nameConfirmed, String description, List<String> stylePoints) {
    }

    public record ShapeDef(String label, String description) {
    }

    public record StyleDef(String code, String name, String description, String imageKey, List<String> itemKeywords) {
    }

    public record FeatureLabel(String label, String unit, String description, int aiValueMultiplier) {
    }

    public record WarningDef(WarningSeverity severity, String message) {
    }

    record Snapshot(
            String ruleVersion,
            String headlineTemplate,
            Map<String, TypeDef> types,
            Map<FaceShape, ShapeDef> faces,
            Map<BodyType, ShapeDef> bodies,
            Map<HairStyleCode, StyleDef> hairStyles,
            Map<FashionStyleCode, StyleDef> fashionStyles,
            Map<RuleScore.NaturalKey, Integer> scores,
            Map<String, String> reasons,
            Map<String, FeatureLabel> features,
            Map<String, String> sizeKoreaItems,
            Map<String, String> sizeKoreaReferences,
            Map<String, WarningDef> warnings,
            WarningDef undefinedWarning) {
    }

    // ------------------------------------------------------------------ cache

    @EventListener
    public void onSeedLoaded(SeedLoadedEvent event) {
        snapshot = null;
    }

    Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = load();
                    snapshot = s;
                }
            }
        }
        return s;
    }

    private Snapshot load() {
        Map<String, String> meta = new HashMap<>();
        seedMetaRepository.findAll().forEach(m -> meta.put(m.getMetaKey(), m.getMetaValue()));

        Map<String, TypeDef> types = new HashMap<>();
        typeDefinitionRepository.findAll().forEach(t -> types.put(typeKey(t.getBodyType(), t.getFaceShape()),
                new TypeDef(t.getTypeCode(), t.getTypeName(), t.isNameConfirmed(), t.getDescription(), List.copyOf(t.getStylePoints()))));

        Map<FaceShape, ShapeDef> faces = new EnumMap<>(FaceShape.class);
        faceShapeDefRepository.findAll().forEach(f -> faces.put(f.getFaceShape(), new ShapeDef(f.getLabel(), f.getDescription())));
        Map<BodyType, ShapeDef> bodies = new EnumMap<>(BodyType.class);
        bodyTypeDefRepository.findAll().forEach(b -> bodies.put(b.getBodyType(), new ShapeDef(b.getLabel(), b.getDescription())));

        Map<HairStyleCode, StyleDef> hair = new EnumMap<>(HairStyleCode.class);
        hairStyleRepository.findAll().forEach(h -> hair.put(h.getStyleCode(),
                new StyleDef(h.getCode(), h.getName(), h.getDescription(), h.getImageKey(), List.of())));
        Map<FashionStyleCode, StyleDef> fashion = new EnumMap<>(FashionStyleCode.class);
        fashionStyleRepository.findAll().forEach(f -> fashion.put(f.getStyleCode(),
                new StyleDef(f.getCode(), f.getName(), f.getDescription(), f.getImageKey(), List.copyOf(f.getItemKeywords()))));

        Map<RuleScore.NaturalKey, Integer> scores = new HashMap<>();
        ruleScoreRepository.findAll().forEach(s -> scores.put(s.naturalKey(), s.getScore()));
        Map<String, String> reasons = new HashMap<>();
        ruleReasonRepository.findAll().forEach(r -> reasons.put(r.getReasonCode(), r.getReasonText()));

        Map<String, FeatureLabel> features = new HashMap<>();
        Map<String, String> skItems = new HashMap<>();
        Map<String, String> skRefs = new HashMap<>();
        Map<String, WarningDef> warnings = new HashMap<>();
        WarningDef undefined = new WarningDef(WarningSeverity.INFO, "촬영 상태를 한 번 더 확인해 주세요.");
        for (AnalysisLabel l : analysisLabelRepository.findAll()) {
            switch (l.getCategory()) {
                case FEATURE -> features.put(l.getLabelCode(), new FeatureLabel(l.getDisplayText(), l.getUnit(), l.getDescription(),
                        l.getAiValueMultiplier() == null ? 1 : l.getAiValueMultiplier()));
                case SIZE_KOREA_ITEM -> skItems.put(l.getLabelCode(), l.getDisplayText());
                case SIZE_KOREA_REFERENCE -> skRefs.put(l.getLabelCode(), l.getDisplayText());
                case WARNING -> warnings.put(l.getLabelCode(), new WarningDef(l.getSeverity(), l.getDisplayText()));
                case WARNING_FALLBACK -> undefined = new WarningDef(l.getSeverity(), l.getDisplayText());
            }
        }
        if (types.isEmpty() || hair.isEmpty() || fashion.isEmpty() || scores.isEmpty()) {
            throw new IllegalStateException("시드 데이터가 적재되지 않았습니다 (moda.seed.mode=none이면 DB에 시드가 미리 있어야 합니다).");
        }
        return new Snapshot(meta.get(SeedMeta.RULE_VERSION), meta.get(SeedMeta.HEADLINE_TEMPLATE),
                Map.copyOf(types), faces, bodies, hair, fashion, Map.copyOf(scores), Map.copyOf(reasons),
                Map.copyOf(features), Map.copyOf(skItems), Map.copyOf(skRefs), Map.copyOf(warnings), undefined);
    }

    private static String typeKey(BodyType body, FaceShape face) {
        return body.key() + "|" + face.key();
    }

    // ------------------------------------------------------------------ queries

    public String ruleVersion() {
        return snapshot().ruleVersion();
    }

    public String headlineTemplate() {
        return snapshot().headlineTemplate();
    }

    public TypeDef type(BodyType body, FaceShape face) {
        return require(snapshot().types().get(typeKey(body, face)), "type " + typeKey(body, face));
    }

    public ShapeDef face(FaceShape face) {
        return require(snapshot().faces().get(face), "face " + face);
    }

    public ShapeDef body(BodyType body) {
        return require(snapshot().bodies().get(body), "body " + body);
    }

    public CodeLabel faceLabel(FaceShape face) {
        return face == null ? null : new CodeLabel(face.key(), face(face).label());
    }

    public CodeLabel bodyLabel(BodyType body) {
        return body == null ? null : new CodeLabel(body.key(), body(body).label());
    }

    public List<CodeLabel> faceOptions() {
        return Arrays.stream(FaceShape.values()).map(this::faceLabel).toList();
    }

    public List<CodeLabel> bodyOptions() {
        return Arrays.stream(BodyType.values()).map(this::bodyLabel).toList();
    }

    public StyleDef hairStyle(HairStyleCode code) {
        return require(snapshot().hairStyles().get(code), "hairStyle " + code);
    }

    public StyleDef fashionStyle(FashionStyleCode code) {
        return require(snapshot().fashionStyles().get(code), "fashionStyle " + code);
    }

    /** 점수표 한 칸. 시드 검증에서 표 전체(0점 포함)가 보장되므로 없으면 시드 불일치로 간주합니다. */
    public int score(RuleDomain domain, ScoreType type, String factor, String conditionValue, String styleCode) {
        RuleScore.NaturalKey key = new RuleScore.NaturalKey(domain, type, factor, conditionValue, styleCode);
        return require(snapshot().scores().get(key), "score " + key);
    }

    public Optional<String> reasonText(String reasonCode) {
        return Optional.ofNullable(snapshot().reasons().get(reasonCode));
    }

    public Optional<FeatureLabel> featureLabel(String key) {
        return Optional.ofNullable(snapshot().features().get(key));
    }

    public Optional<String> sizeKoreaItemLabel(String key) {
        return Optional.ofNullable(snapshot().sizeKoreaItems().get(key));
    }

    public Optional<String> sizeKoreaReferenceLabel(String reference) {
        return Optional.ofNullable(snapshot().sizeKoreaReferences().get(reference));
    }

    /** 경고 코드의 severity·문구. 정의되지 않은 코드는 기본값(INFO, “촬영 상태를 한 번 더 확인해 주세요.”). */
    public WarningDef warning(String code) {
        Snapshot s = snapshot();
        return s.warnings().getOrDefault(code, s.undefinedWarning());
    }

    public boolean isKnownWarning(String code) {
        return snapshot().warnings().containsKey(code);
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("시드 데이터에 " + what + " 항목이 없습니다.");
        }
        return value;
    }
}
