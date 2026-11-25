package com.moda.recommendation;

import com.moda.common.enums.FashionStyleCode;
import com.moda.common.enums.HairStyleCode;
import com.moda.recommendation.dto.RecommendationDtos.Adjustment;
import com.moda.recommendation.dto.RecommendationDtos.FashionRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.HairRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.ScoreBreakdown;
import com.moda.recommendation.dto.RecommendationDtos.TypeResult;
import com.moda.seed.catalog.SeedCatalog;
import com.moda.seed.catalog.SeedCatalog.StyleDef;
import com.moda.seed.catalog.SeedCatalog.TypeDef;
import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.ScoreType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 결정적(Deterministic) 규칙 기반 추천 엔진 (docs/style-rules.md 2장 ~ 5장).
 * <ol>
 *   <li>점수 = 기본 점수(1차 기준) + 보정 점수(보조 기준) 합. 헤어: 얼굴형 + 모질 3항목 / 패션: 체형 + BMI·키·나이 구간</li>
 *   <li>정렬: 최종 점수 DESC → 기본 점수 DESC → 코드 선언 순서(ordinal) ASC. 헤어 상위 2개, 패션 상위 3개</li>
 *   <li>근거(reasons) 최대 2문장: ① 기본 점수 근거(4점 이상 전용 문구, 없으면 일반 문구)
 *       ② 양수 보정 중 delta 최대 1개(동점이면 헤어 굵기→곱슬→숱, 패션 BMI→키→나이). 음수 보정은 문구로 노출하지 않음</li>
 * </ol>
 * 점수표·문구는 모두 시드({@link SeedCatalog})에서 읽습니다.
 */
@Service
public class StyleRecommendationService {

    public static final int HAIR_TOP_N = 2;
    public static final int FASHION_TOP_N = 3;
    /** 기본 점수가 이 값 이상이면 전용 문구 사용 (style-rules.md 3.3, 4.3). */
    public static final int SPECIFIC_REASON_MIN_BASE_SCORE = 4;
    public static final String TYPE_NAME_PLACEHOLDER = "{typeName}";

    static final String BMI_FACTOR = "bmi";
    /** BMI 구간은 응답에 노출하지 않음 (R-11, api-spec.md 3.6). */
    public static final String HIDDEN = "HIDDEN";

    private final SeedCatalog catalog;

    public StyleRecommendationService(SeedCatalog catalog) {
        this.catalog = catalog;
    }

    public StyleRecommendation recommend(RecommendationInput in) {
        return new StyleRecommendation(catalog.ruleVersion(), typeResult(in), recommendHair(in), recommendFashion(in));
    }

    // ------------------------------------------------------------------ 맞춤 유형 (5장)

    TypeResult typeResult(RecommendationInput in) {
        TypeDef t = catalog.type(in.bodyType(), in.faceShape());
        String headline = catalog.headlineTemplate().replace(TYPE_NAME_PLACEHOLDER, t.typeName());
        return new TypeResult(t.typeCode(), t.typeName(), t.nameConfirmed(), headline, t.description(), t.stylePoints());
    }

    // ------------------------------------------------------------------ 헤어 (3장)

    List<HairRecommendation> recommendHair(RecommendationInput in) {
        String face = in.faceShape().key();
        List<Factor> factors = List.of(
                new Factor("hairThickness", "THICKNESS", in.hairThickness().name()),
                new Factor("hairCurl", "CURL", in.hairCurl().name()),
                new Factor("hairDensity", "DENSITY", in.hairDensity().name()));

        List<Scored<HairStyleCode>> scored = new ArrayList<>();
        for (HairStyleCode style : HairStyleCode.values()) {
            int base = catalog.score(RuleDomain.HAIR, ScoreType.BASE, "faceShape", face, style.name());
            scored.add(score(RuleDomain.HAIR, style, base, factors));
        }
        List<Scored<HairStyleCode>> top = rank(scored, HAIR_TOP_N);

        List<HairRecommendation> result = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Scored<HairStyleCode> s = top.get(i);
            StyleDef def = catalog.hairStyle(s.style());
            Reasons reasons = reasons(s, "FACE_" + face.toUpperCase(Locale.ROOT) + "_" + s.style().name(),
                    "HAIR_GENERIC_" + s.style().name());
            result.add(new HairRecommendation(i + 1, s.style(), def.name(), def.description(), def.imageKey(), s.score(),
                    reasons.texts(), reasons.codes(), breakdown(s)));
        }
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------ 패션 (4장)

    List<FashionRecommendation> recommendFashion(RecommendationInput in) {
        String body = in.bodyType().key();
        List<Factor> factors = new ArrayList<>();
        BodyMetrics.bmiBand(in.heightCm(), in.weightKg())                         // weightKg = null 이면 BMI 보정 생략
                .ifPresent(band -> factors.add(new Factor(BMI_FACTOR, null, band.name())));
        factors.add(new Factor("height", null, BodyMetrics.heightBand(in.heightCm()).name()));
        factors.add(new Factor("age", null, BodyMetrics.ageBand(in.age()).name()));

        List<Scored<FashionStyleCode>> scored = new ArrayList<>();
        for (FashionStyleCode style : FashionStyleCode.values()) {
            int base = catalog.score(RuleDomain.FASHION, ScoreType.BASE, "bodyType", body, style.name());
            scored.add(score(RuleDomain.FASHION, style, base, factors));
        }
        List<Scored<FashionStyleCode>> top = rank(scored, FASHION_TOP_N);

        List<FashionRecommendation> result = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Scored<FashionStyleCode> s = top.get(i);
            StyleDef def = catalog.fashionStyle(s.style());
            Reasons reasons = reasons(s, "BODY_" + body.toUpperCase(Locale.ROOT) + "_" + s.style().name(),
                    "FASHION_GENERIC_" + s.style().name());
            result.add(new FashionRecommendation(i + 1, s.style(), def.name(), def.description(), def.imageKey(), s.score(),
                    reasons.texts(), reasons.codes(), breakdown(s), def.itemKeywords()));
        }
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------ 공통

    /**
     * @param factor    보정 factor (헤어: hairThickness/hairCurl/hairDensity, 패션: bmi/height/age)
     * @param codeToken 보정 코드 토큰 (헤어: THICKNESS/CURL/DENSITY, 패션: null — 구간명이 곧 토큰)
     * @param value     입력 Enum 값 또는 구간명
     */
    private record Factor(String factor, String codeToken, String value) {

        String adjustmentCode(String style) {
            return codeToken == null ? "ADJ_" + value + "_" + style : "ADJ_" + codeToken + "_" + value + "_" + style;
        }
    }

    /** @param reasonCode 시드의 근거 문구 코드(내부 조회용), {@code code}·{@code value}는 응답 표시용(BMI는 마스킹) */
    private record Adj(String code, String factor, String value, int delta, String reasonCode) {
    }

    private record Scored<S extends Enum<S>>(S style, int base, List<Adj> adjustments, int score) {
    }

    private record Reasons(List<String> texts, List<String> codes) {
    }

    private <S extends Enum<S>> Scored<S> score(RuleDomain domain, S style, int base, List<Factor> factors) {
        List<Adj> adjs = new ArrayList<>();
        int sum = 0;
        for (Factor f : factors) {
            int delta = catalog.score(domain, ScoreType.ADJUSTMENT, f.factor(), f.value(), style.name());
            if (delta == 0) {
                continue;                                                             // delta ≠ 0 인 보정만 기록
            }
            sum += delta;
            String seedCode = f.adjustmentCode(style.name());
            boolean bmi = BMI_FACTOR.equals(f.factor());
            adjs.add(new Adj(bmi ? maskBmiCode(style.name()) : seedCode, f.factor(), bmi ? HIDDEN : f.value(), delta, seedCode));
        }
        return new Scored<>(style, base, List.copyOf(adjs), base + sum);
    }

    /** 최종 점수 DESC → 기본 점수 DESC → ordinal ASC (style-rules.md 2장 3번). */
    private static <S extends Enum<S>> List<Scored<S>> rank(List<Scored<S>> scored, int topN) {
        return scored.stream()
                .sorted(Comparator.comparingInt((Scored<S> s) -> s.score()).reversed()
                        .thenComparing(Comparator.comparingInt((Scored<S> s) -> s.base()).reversed())
                        .thenComparingInt(s -> s.style().ordinal()))
                .limit(topN)
                .toList();
    }

    private <S extends Enum<S>> Reasons reasons(Scored<S> s, String specificCode, String genericCode) {
        List<String> texts = new ArrayList<>(2);
        List<String> codes = new ArrayList<>(2);

        // ① 기본 점수 근거: 4점 이상이면 전용 문구, 전용 문구가 없는 조합은 일반 문구
        String baseCode = s.base() >= SPECIFIC_REASON_MIN_BASE_SCORE && catalog.reasonText(specificCode).isPresent()
                ? specificCode : genericCode;
        catalog.reasonText(baseCode).ifPresent(text -> {
            texts.add(text);
            codes.add(baseCode);
        });

        // ② 양수 보정 중 delta 최대 1개. 동점이면 factor 순서(헤어 굵기→곱슬→숱, 패션 BMI→키→나이)의 앞쪽
        Adj best = null;
        for (Adj a : s.adjustments()) {
            if (a.delta() > 0 && (best == null || a.delta() > best.delta())) {
                best = a;
            }
        }
        if (best != null) {
            Adj chosen = best;
            catalog.reasonText(chosen.reasonCode()).ifPresent(text -> {
                texts.add(text);
                codes.add(chosen.code());
            });
        }
        return new Reasons(List.copyOf(texts), List.copyOf(codes));
    }

    private static <S extends Enum<S>> ScoreBreakdown breakdown(Scored<S> s) {
        return new ScoreBreakdown(s.base(), s.adjustments().stream()
                .map(a -> new Adjustment(a.code(), a.factor(), a.value(), a.delta()))
                .toList());
    }

    /** BMI 보정 코드도 구간명이 드러나지 않도록 {@code ADJ_BMI_HIDDEN_{패션code}}로 응답합니다 (R-11). */
    static String maskBmiCode(String style) {
        return "ADJ_BMI_" + HIDDEN + "_" + style;
    }
}
