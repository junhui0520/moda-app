package com.moda.session.dto;

import com.moda.common.dto.CodeLabel;
import com.moda.common.enums.AnalysisSource;
import com.moda.common.enums.AnalysisType;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import com.moda.common.enums.NextStep;
import com.moda.common.enums.WarningSeverity;
import com.moda.recommendation.dto.RecommendationDtos.FashionRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.HairRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.TypeResult;
import java.time.OffsetDateTime;
import java.util.List;

/** 응답 {@code data} 객체 (docs/api-spec.md 3장, API ①~⑤·⑦·⑧). 값이 없으면 키를 생략하지 않고 {@code null}입니다. */
public final class SessionResponses {

    private SessionResponses() {
    }

    /** 3.2 */
    public record ProgressDto(boolean profileInput, boolean faceAnalysis, boolean bodyAnalysis) {
    }

    /** 3.3 */
    public record WarningDto(String code, WarningSeverity severity, String message) {
    }

    /** 3.10 */
    public record ProfileInputDto(
            Integer age,
            Integer heightCm,
            Integer weightKg,
            HairThickness hairThickness,
            HairCurl hairCurl,
            HairDensity hairDensity) {
    }

    /** ① */
    public record GuestSessionData(
            String sessionId,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            int ttlMinutes,
            ProgressDto progress,
            NextStep nextStep) {
    }

    /** ② */
    public record ProfileInputData(
            String sessionId,
            ProfileInputDto profileInput,
            boolean bodyAnalysisReset,
            ProgressDto progress,
            NextStep nextStep,
            OffsetDateTime expiresAt) {
    }

    /** ③ */
    public record FaceAnalysisData(
            String sessionId,
            AnalysisType analysisType,
            boolean usable,
            boolean retakeRequired,
            CodeLabel faceShape,
            Double confidence,
            Boolean borderline,
            List<WarningDto> warnings,
            ProgressDto progress,
            NextStep nextStep,
            OffsetDateTime analyzedAt,
            OffsetDateTime expiresAt) {
    }

    /** ④ */
    public record BodyAnalysisData(
            String sessionId,
            AnalysisType analysisType,
            boolean usable,
            boolean retakeRequired,
            CodeLabel bodyType,
            Double confidence,
            Boolean borderline,
            int heightCmUsed,
            List<WarningDto> warnings,
            ProgressDto progress,
            NextStep nextStep,
            OffsetDateTime analyzedAt,
            OffsetDateTime expiresAt) {
    }

    /** 3.11 {@code ResultData} (⑤, ⑦ 공통). */
    public record ResultData(
            String sessionId,
            String ruleVersion,
            OffsetDateTime generatedAt,
            TypeResult type,
            ResultBasis basis,
            List<HairRecommendation> hairStyles,
            List<FashionRecommendation> fashionStyles,
            String disclaimer) {
    }

    /** 3.11 {@code ResultBasis}. */
    public record ResultBasis(
            CodeLabel faceShape,
            CodeLabel bodyType,
            AnalysisSource faceSource,
            AnalysisSource bodySource) {
    }

    /** ⑦ */
    public record ProfilePatchData(
            String sessionId,
            List<String> changedFields,
            ProgressDto progress,
            NextStep nextStep,
            OffsetDateTime expiresAt,
            ResultData result) {
    }

    /** ⑧ */
    public record SessionDeleteData(
            String sessionId,
            boolean deleted,
            boolean alreadyDeleted,
            int deletedPhotoCount,
            OffsetDateTime deletedAt) {
    }
}
