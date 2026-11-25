package com.moda.chat.model;

import com.moda.common.dto.CodeLabel;
import com.moda.common.enums.AnalysisSource;
import com.moda.common.enums.AnalysisStatus;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import com.moda.recommendation.dto.RecommendationDtos.FashionRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.HairRecommendation;
import java.util.List;

/**
 * 챗봇 1턴에 쓰는 세션 컨텍스트 스냅샷. 매 턴 DB의 최신 세션 값(⑦ 보정 포함)으로 새로 만듭니다.
 * <p><b>몸무게·BMI 필드는 의도적으로 없습니다</b> — 시스템 프롬프트·Mock 답변·추천 칩 어디에도 체중 수치가 들어갈 수 없도록
 * 구조적으로 차단합니다 (R-11). BMI는 이미 추천 결과(패션 순위)에만 반영되어 있습니다.
 */
public record ChatContext(
        String typeCode,
        String typeName,
        String typeDescription,
        List<String> stylePoints,
        CodeLabel faceShape,
        AnalysisStatus faceStatus,
        AnalysisSource faceSource,
        String faceDescription,
        String faceAiReason,
        CodeLabel bodyType,
        AnalysisStatus bodyStatus,
        AnalysisSource bodySource,
        String bodyDescription,
        Integer age,
        Integer heightCm,
        HairThickness hairThickness,
        HairCurl hairCurl,
        HairDensity hairDensity,
        List<HairRecommendation> hairStyles,
        List<FashionRecommendation> fashionStyles) {

    public HairRecommendation topHair() {
        return hairStyles.get(0);
    }

    public HairRecommendation secondHair() {
        return hairStyles.size() > 1 ? hairStyles.get(1) : hairStyles.get(0);
    }

    public FashionRecommendation topFashion() {
        return fashionStyles.get(0);
    }

    public FashionRecommendation secondFashion() {
        return fashionStyles.size() > 1 ? fashionStyles.get(1) : fashionStyles.get(0);
    }

    /** "큰 사각 체형" → 그대로, "작은 역삼각형" → "작은 역삼각형 체형". */
    public String bodyPhrase() {
        String label = bodyType.label();
        return label.endsWith("체형") ? label : label + " 체형";
    }
}
