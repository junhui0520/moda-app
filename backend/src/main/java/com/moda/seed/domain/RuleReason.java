package com.moda.seed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 추천 근거 문구 (docs/style-rules.md 3.3, 3.4, 4.3, 4.4).
 * <ul>
 *   <li>BASE_SPECIFIC: factor=faceShape|bodyType, conditionValue=얼굴형/체형 Key</li>
 *   <li>BASE_GENERIC: factor·conditionValue = null</li>
 *   <li>ADJUSTMENT: factor=hairThickness|hairCurl|hairDensity|bmi|height|age, conditionValue=Enum/구간명</li>
 * </ul>
 */
@Entity
@Table(name = "rule_reason")
public class RuleReason {

    @Id
    @Column(name = "reason_code", length = 80)
    private String reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_domain", nullable = false, length = 20)
    private RuleDomain ruleDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 20)
    private ReasonType reasonType;

    @Column(name = "factor", length = 30)
    private String factor;

    @Column(name = "condition_value", length = 30)
    private String conditionValue;

    @Column(name = "style_code", nullable = false, length = 30)
    private String styleCode;

    @Column(name = "reason_text", nullable = false, length = 500)
    private String reasonText;

    protected RuleReason() {
    }

    public RuleReason(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public void update(RuleDomain ruleDomain, ReasonType reasonType, String factor, String conditionValue,
                       String styleCode, String reasonText) {
        this.ruleDomain = ruleDomain;
        this.reasonType = reasonType;
        this.factor = factor;
        this.conditionValue = conditionValue;
        this.styleCode = styleCode;
        this.reasonText = reasonText;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public RuleDomain getRuleDomain() {
        return ruleDomain;
    }

    public ReasonType getReasonType() {
        return reasonType;
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

    public String getReasonText() {
        return reasonText;
    }
}
