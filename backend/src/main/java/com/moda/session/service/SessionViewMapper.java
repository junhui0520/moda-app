package com.moda.session.service;

import com.moda.ai.model.AnalysisValues.CandidateValue;
import com.moda.ai.model.AnalysisValues.FeatureValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaValue;
import com.moda.ai.model.AnalysisValues.WarningValue;
import com.moda.common.dto.CodeLabel;
import com.moda.common.enums.AnalysisType;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.NextStep;
import com.moda.common.enums.SizeKoreaDirection;
import com.moda.common.enums.WarningSeverity;
import com.moda.global.time.KstTime;
import com.moda.seed.catalog.SeedCatalog;
import com.moda.seed.catalog.SeedCatalog.FeatureLabel;
import com.moda.session.domain.GuestSession;
import com.moda.session.dto.AnalysisDetailResponses.BodyDetail;
import com.moda.session.dto.AnalysisDetailResponses.CandidateDto;
import com.moda.session.dto.AnalysisDetailResponses.FaceDetail;
import com.moda.session.dto.AnalysisDetailResponses.FeatureDto;
import com.moda.session.dto.AnalysisDetailResponses.SizeKoreaCompareDto;
import com.moda.session.dto.AnalysisDetailResponses.SizeKoreaItemDto;
import com.moda.session.dto.AnalysisDetailResponses.SizeKoreaSummaryDto;
import com.moda.session.dto.SessionResponses.ProfileInputDto;
import com.moda.session.dto.SessionResponses.ProgressDto;
import com.moda.session.dto.SessionResponses.WarningDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 세션 상태 → 응답 DTO 변환, AI 경고 정규화. 라벨·문구는 응답 시점에 시드({@link SeedCatalog})에서 붙입니다 (R-07).
 */
@Component
public class SessionViewMapper {

    static final String ANALYSIS_UNUSABLE = "ANALYSIS_UNUSABLE";
    static final String FACE_BORDERLINE = "FACE_BORDERLINE";
    static final String BODY_BORDERLINE = "BODY_BORDERLINE";
    static final String DEFAULT_FEATURE_UNIT = "ratio";
    /** |deviation| &lt; 0.02 → SIMILAR (api-spec.md 2장). */
    static final double SIMILAR_THRESHOLD = 0.02;

    private final SeedCatalog catalog;

    public SessionViewMapper(SeedCatalog catalog) {
        this.catalog = catalog;
    }

    // ------------------------------------------------------------------ progress / nextStep

    public ProgressDto progress(GuestSession s) {
        return new ProgressDto(s.hasProfile(), s.getFaceResult() != null, s.getBodyResult() != null);
    }

    /** api-spec.md 2장 nextStep 결정 규칙. */
    public NextStep nextStep(GuestSession s) {
        if (!s.hasProfile()) {
            return NextStep.PROFILE_INPUT;
        }
        if (s.getFaceResult() == null) {
            return NextStep.FACE_ANALYSIS;
        }
        if (s.getBodyResult() == null) {
            return NextStep.BODY_ANALYSIS;
        }
        return NextStep.RESULT_READY;
    }

    public ProfileInputDto profileInput(GuestSession s) {
        if (!s.hasProfile()) {
            return null;
        }
        return new ProfileInputDto(s.getAge(), s.getHeightCm(), s.getWeightKg(), s.getHairThickness(), s.getHairCurl(), s.getHairDensity());
    }

    // ------------------------------------------------------------------ warnings (A.5 규칙)

    /**
     * AI 경고 코드 정규화 (api-spec.md 부록 A.5).
     * <ol>
     *   <li>코드별 severity·문구는 시드에서, 정의되지 않은 코드는 INFO</li>
     *   <li>{@code usable=true}인데 RETAKE 코드가 섞여 있으면 INFO로 낮춤</li>
     *   <li>{@code usable=false}인데 RETAKE 코드가 없으면 {@code ANALYSIS_UNUSABLE} 추가</li>
     *   <li>{@code borderline=true}이면 {@code FACE_BORDERLINE}/{@code BODY_BORDERLINE} 추가 ({@code null}이면 추가하지 않음)</li>
     * </ol>
     */
    public List<WarningValue> normalizeWarnings(List<String> codes, boolean usable, Boolean borderline, AnalysisType type) {
        Map<String, WarningSeverity> result = new LinkedHashMap<>();
        for (String code : codes) {
            WarningSeverity severity = catalog.warning(code).severity();
            if (usable && severity == WarningSeverity.RETAKE) {
                severity = WarningSeverity.INFO;
            }
            result.putIfAbsent(code, severity);
        }
        if (!usable && !result.containsValue(WarningSeverity.RETAKE)) {
            result.put(ANALYSIS_UNUSABLE, WarningSeverity.RETAKE);
        }
        if (usable && Boolean.TRUE.equals(borderline)) {
            result.putIfAbsent(type == AnalysisType.FACE ? FACE_BORDERLINE : BODY_BORDERLINE, WarningSeverity.INFO);
        }
        return result.entrySet().stream().map(e -> new WarningValue(e.getKey(), e.getValue())).toList();
    }

    public List<WarningDto> warnings(List<WarningValue> values) {
        return values.stream()
                .map(w -> new WarningDto(w.code(), w.severity(), catalog.warning(w.code()).message()))
                .toList();
    }

    // ------------------------------------------------------------------ ⑥ detail

    public FaceDetail faceDetail(GuestSession s) {
        FaceShape result = s.getFaceResult();
        return new FaceDetail(
                s.faceStatus(),
                s.getFaceSource(),
                catalog.faceLabel(result),
                catalog.faceLabel(s.getFaceAiResult()),
                result == null ? null : catalog.face(result).description(),
                s.getFaceAiResult() == null ? null : s.getFaceAiReason(),
                s.getFaceConfidence(),
                s.getFaceBorderline(),
                candidates(s.getFaceCandidates(), true),
                warnings(s.getFaceWarnings()),
                features(s.getFaceFeatures()),
                KstTime.toOffset(s.getFaceAnalyzedAt()));
    }

    public BodyDetail bodyDetail(GuestSession s) {
        BodyType result = s.getBodyResult();
        return new BodyDetail(
                s.bodyStatus(),
                s.getBodySource(),
                catalog.bodyLabel(result),
                catalog.bodyLabel(s.getBodyAiResult()),
                result == null ? null : catalog.body(result).description(),
                s.getBodyConfidence(),
                s.getBodyBorderline(),
                candidates(s.getBodyCandidates(), false),
                warnings(s.getBodyWarnings()),
                features(s.getBodyFeatures()),
                s.getBodyHeightCmUsed(),
                sizeKorea(s.getBodyCompareToSizeKorea(), s.getBodyHeightCmUsed()),
                KstTime.toOffset(s.getBodyAnalyzedAt()));
    }

    private List<CandidateDto> candidates(List<CandidateValue> values, boolean face) {
        return values.stream()
                .map(c -> new CandidateDto(c.key(), face
                        ? catalog.faceLabel(FaceShape.fromKey(c.key())).label()
                        : catalog.bodyLabel(BodyType.fromKey(c.key())).label(), c.probability()))
                .toList();
    }

    /** 라벨이 없는 키는 key를 그대로 라벨로 사용 (api-spec.md 3.8). 체형 편차는 배율(×100)로 percent 변환. */
    private List<FeatureDto> features(List<FeatureValue> values) {
        return values.stream().map(f -> {
            FeatureLabel label = catalog.featureLabel(f.key()).orElse(null);
            if (label == null) {
                return new FeatureDto(f.key(), f.key(), round(f.value(), 3), DEFAULT_FEATURE_UNIT, null);
            }
            return new FeatureDto(f.key(), label.label(), round(f.value() * label.aiValueMultiplier(), 3), label.unit(), label.description());
        }).toList();
    }

    private SizeKoreaCompareDto sizeKorea(SizeKoreaValue v, Integer heightCmUsed) {
        if (v == null) {
            return null;
        }
        List<SizeKoreaItemDto> items = v.items().stream()
                .map(i -> new SizeKoreaItemDto(i.key(), catalog.sizeKoreaItemLabel(i.key()).orElse(i.key()), i.userMm(), i.refMm(),
                        i.deviation(), round(i.deviation() * 100, 1), direction(i.deviation())))
                .toList();
        SizeKoreaSummaryDto summary = v.summary() == null ? null
                : new SizeKoreaSummaryDto(v.summary().shoulderDeviation(), v.summary().trunkDeviation());
        return new SizeKoreaCompareDto(v.reference(), catalog.sizeKoreaReferenceLabel(v.reference()).orElse(v.reference()),
                v.heightCm() != null ? v.heightCm() : heightCmUsed, items, summary);
    }

    static SizeKoreaDirection direction(double deviation) {
        if (Math.abs(deviation) < SIMILAR_THRESHOLD) {
            return SizeKoreaDirection.SIMILAR;
        }
        return deviation > 0 ? SizeKoreaDirection.ABOVE : SizeKoreaDirection.BELOW;
    }

    static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public List<CodeLabel> faceOptions() {
        return catalog.faceOptions();
    }

    public List<CodeLabel> bodyOptions() {
        return catalog.bodyOptions();
    }
}
