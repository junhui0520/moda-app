package com.moda.seed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 추천 점수표 한 칸 (docs/style-rules.md 3.1, 3.2, 4.1, 4.2). 0점 칸도 모두 저장해 표 전체를 보존합니다.
 * <ul>
 *   <li>헤어 BASE: factor=faceShape, conditionValue=얼굴형 Key</li>
 *   <li>헤어 ADJUSTMENT: factor=hairThickness|hairCurl|hairDensity, conditionValue=모질 Enum</li>
 *   <li>패션 BASE: factor=bodyType, conditionValue=체형 Key</li>
 *   <li>패션 ADJUSTMENT: factor=bmi|height|age, conditionValue=구간명</li>
 * </ul>
 * 자연 키: (ruleDomain, scoreType, factor, conditionValue, styleCode)
 */
@Entity
@Table(name = "rule_score",
        uniqueConstraints = @UniqueConstraint(name = "uk_rule_score_natural",
                columnNames = {"rule_domain", "score_type", "factor", "condition_value", "style_code"}))
public class RuleScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_domain", nullable = false, length = 20)
    private RuleDomain ruleDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", nullable = false, length = 20)
    private ScoreType scoreType;

    @Column(name = "factor", nullable = false, length = 30)
    private String factor;

    @Column(name = "condition_value", nullable = false, length = 30)
    private String conditionValue;

    @Column(name = "style_code", nullable = false, length = 30)
    private String styleCode;

    @Column(name = "score", nullable = false)
    private int score;

    protected RuleScore() {
    }

    public RuleScore(RuleDomain ruleDomain, ScoreType scoreType, String factor, String conditionValue, String styleCode, int score) {
        this.ruleDomain = ruleDomain;
        this.scoreType = scoreType;
        this.factor = factor;
        this.conditionValue = conditionValue;
        this.styleCode = styleCode;
        this.score = score;
    }

    public NaturalKey naturalKey() {
        return new NaturalKey(ruleDomain, scoreType, factor, conditionValue, styleCode);
    }

    public void updateScore(int score) {
        this.score = score;
    }

    public Long getId() {
        return id;
    }

    public RuleDomain getRuleDomain() {
        return ruleDomain;
    }

    public ScoreType getScoreType() {
        return scoreType;
    }

    public String getFactor() {
        return factor;
    }

    public String getConditionValue() {
        return conditionValue;
    }

    public String getStyleCode() {
        return styleCode;
    }

    public int getScore() {
        return score;
    }

    public record NaturalKey(RuleDomain ruleDomain, ScoreType scoreType, String factor, String conditionValue, String styleCode) {
    }
}
