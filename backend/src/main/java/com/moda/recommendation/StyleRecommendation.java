package com.moda.recommendation;

import com.moda.recommendation.dto.RecommendationDtos.FashionRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.HairRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.TypeResult;
import java.util.List;

/** 추천 엔진 출력: 맞춤 유형 + 헤어 Top 2 + 패션 Top 3. */
public record StyleRecommendation(
        String ruleVersion,
        TypeResult type,
        List<HairRecommendation> hairStyles,
        List<FashionRecommendation> fashionStyles) {
}
