package com.moda.seed.domain;

import com.moda.common.enums.WarningSeverity;
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
 * AI 분석 라벨·경고 문구 (docs/api-spec.md 3.9, 부록 A.4, A.5).
 * <ul>
 *   <li>FEATURE: labelCode=features 키, displayText=화면 라벨, unit, description, target, aiValueMultiplier, note</li>
 *   <li>SIZE_KOREA_REFERENCE: labelCode=기준 코드, displayText=기준 라벨</li>
 *   <li>SIZE_KOREA_ITEM: labelCode=항목 키, displayText=화면 라벨, sourceName=Size Korea 원본 항목명</li>
 *   <li>WARNING: labelCode=경고 코드, displayText=앱 표시 message, severity, target, criteria</li>
 *   <li>WARNING_FALLBACK: labelCode={@value #UNDEFINED_WARNING_CODE}, displayText, severity</li>
 * </ul>
 * 자연 키: (category, labelCode)
 */
@Entity
@Table(name = "analysis_label",
        uniqueConstraints = @UniqueConstraint(name = "uk_analysis_label_natural", columnNames = {"category", "label_code"}))
public class AnalysisLabel {

    public static final String UNDEFINED_WARNING_CODE = "UNDEFINED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private LabelCategory category;

    @Column(name = "label_code", nullable = false, length = 50)
    private String labelCode;

    @Column(name = "display_text", nullable = false, length = 500)
    private String displayText;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10)
    private WarningSeverity severity;

    @Column(name = "target", length = 10)
    private String target;

    @Column(name = "source_name", length = 100)
    private String sourceName;

    @Column(name = "criteria", length = 500)
    private String criteria;

    @Column(name = "ai_value_multiplier")
    private Integer aiValueMultiplier;

    @Column(name = "note", length = 300)
    private String note;

    protected AnalysisLabel() {
    }

    public AnalysisLabel(LabelCategory category, String labelCode) {
        this.category = category;
        this.labelCode = labelCode;
    }

    public void update(String displayText, String unit, String description, WarningSeverity severity, String target,
                       String sourceName, String criteria, Integer aiValueMultiplier, String note) {
        this.displayText = displayText;
        this.unit = unit;
        this.description = description;
        this.severity = severity;
        this.target = target;
        this.sourceName = sourceName;
        this.criteria = criteria;
        this.aiValueMultiplier = aiValueMultiplier;
        this.note = note;
    }

    public NaturalKey naturalKey() {
        return new NaturalKey(category, labelCode);
    }

    public Long getId() {
        return id;
    }

    public LabelCategory getCategory() {
        return category;
    }

    public String getLabelCode() {
        return labelCode;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getUnit() {
        return unit;
    }

    public String getDescription() {
        return description;
    }

    public WarningSeverity getSeverity() {
        return severity;
    }

    public String getTarget() {
        return target;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getCriteria() {
        return criteria;
    }

    public Integer getAiValueMultiplier() {
        return aiValueMultiplier;
    }

    public String getNote() {
        return note;
    }

    public record NaturalKey(LabelCategory category, String labelCode) {
    }
}
