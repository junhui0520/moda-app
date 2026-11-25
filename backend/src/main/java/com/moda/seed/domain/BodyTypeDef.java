package com.moda.seed.domain;

import com.moda.common.enums.BodyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 체형 4종 라벨·설명 (docs/style-rules.md 1.3, 6.2). */
@Entity
@Table(name = "body_type_def")
public class BodyTypeDef {

    @Id
    @Column(name = "body_key", length = 30)
    private String bodyKey;

    /** 공식 한글명 (화면 표시). */
    @Column(name = "label", nullable = false, length = 30)
    private String label;

    @Column(name = "body_index", nullable = false)
    private int bodyIndex;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /** AI 코드 내부 한글명 ({@code BODY_LABELS}) — 화면 표시 금지, 참고용. */
    @Column(name = "ai_internal_label", length = 30)
    private String aiInternalLabel;

    /** AI {@code BODY_RULES} 판정 기준 요약(임시) — 참고용. */
    @Column(name = "rule_summary", length = 300)
    private String ruleSummary;

    protected BodyTypeDef() {
    }

    public BodyTypeDef(String bodyKey) {
        this.bodyKey = bodyKey;
    }

    public void update(String label, int bodyIndex, String description, String aiInternalLabel, String ruleSummary) {
        this.label = label;
        this.bodyIndex = bodyIndex;
        this.description = description;
        this.aiInternalLabel = aiInternalLabel;
        this.ruleSummary = ruleSummary;
    }

    public String getBodyKey() {
        return bodyKey;
    }

    public BodyType getBodyType() {
        return BodyType.fromKey(bodyKey);
    }

    public String getLabel() {
        return label;
    }

    public int getBodyIndex() {
        return bodyIndex;
    }

    public String getDescription() {
        return description;
    }

    public String getAiInternalLabel() {
        return aiInternalLabel;
    }

    public String getRuleSummary() {
        return ruleSummary;
    }
}
