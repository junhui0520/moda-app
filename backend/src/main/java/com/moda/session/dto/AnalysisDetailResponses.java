package com.moda.session.dto;

import com.moda.common.dto.CodeLabel;
import com.moda.common.enums.AnalysisSource;
import com.moda.common.enums.AnalysisStatus;
import com.moda.common.enums.SizeKoreaDirection;
import com.moda.session.dto.SessionResponses.ProfileInputDto;
import com.moda.session.dto.SessionResponses.WarningDto;
import java.time.OffsetDateTime;
import java.util.List;

/** ⑥ {@code GET /analysis-detail} 응답 (docs/api-spec.md 3.7 ~ 3.9, ⑥). */
public final class AnalysisDetailResponses {

    private AnalysisDetailResponses() {
    }

    public record AnalysisDetailData(
            String sessionId,
            FaceDetail face,
            BodyDetail body,
            ProfileInputDto profileInput,
            EditableOptions editableOptions) {
    }

    public record EditableOptions(List<CodeLabel> faceShapes, List<CodeLabel> bodyTypes) {
    }

    /** {@code FaceDetail} — {@code aiReason}(v1.2, AI v8)은 얼굴 전용. */
    public record FaceDetail(
            AnalysisStatus status,
            AnalysisSource source,
            CodeLabel result,
            CodeLabel aiResult,
            String description,
            String aiReason,
            Double confidence,
            Boolean borderline,
            List<CandidateDto> candidates,
            List<WarningDto> warnings,
            List<FeatureDto> features,
            OffsetDateTime analyzedAt) {
    }

    /** {@code BodyDetail} = FaceDetail 필드({@code aiReason} 제외) + {@code heightCmUsed}, {@code compareToSizeKorea}. */
    public record BodyDetail(
            AnalysisStatus status,
            AnalysisSource source,
            CodeLabel result,
            CodeLabel aiResult,
            String description,
            Double confidence,
            Boolean borderline,
            List<CandidateDto> candidates,
            List<WarningDto> warnings,
            List<FeatureDto> features,
            Integer heightCmUsed,
            SizeKoreaCompareDto compareToSizeKorea,
            OffsetDateTime analyzedAt) {
    }

    /** 3.7 */
    public record CandidateDto(String key, String label, double probability) {
    }

    /** 3.8 */
    public record FeatureDto(String key, String label, double value, String unit, String description) {
    }

    /** 3.9 */
    public record SizeKoreaCompareDto(
            String reference,
            String referenceLabel,
            Integer heightCm,
            List<SizeKoreaItemDto> items,
            SizeKoreaSummaryDto summary) {
    }

    public record SizeKoreaItemDto(
            String key,
            String label,
            int userMm,
            int refMm,
            double deviation,
            double deviationPercent,
            SizeKoreaDirection direction) {
    }

    public record SizeKoreaSummaryDto(double shoulderDeviation, double trunkDeviation) {
    }
}
