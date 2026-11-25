package com.moda.seed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 시드 메타 정보 (ruleVersion, headlineTemplate 등) key-value. */
@Entity
@Table(name = "seed_meta")
public class SeedMeta {

    public static final String RULE_VERSION = "ruleVersion";
    public static final String HEADLINE_TEMPLATE = "headlineTemplate";

    @Id
    @Column(name = "meta_key", length = 50)
    private String metaKey;

    @Column(name = "meta_value", nullable = false, length = 500)
    private String metaValue;

    protected SeedMeta() {
    }

    public SeedMeta(String metaKey, String metaValue) {
        this.metaKey = metaKey;
        this.metaValue = metaValue;
    }

    public void update(String metaValue) {
        this.metaValue = metaValue;
    }

    public String getMetaKey() {
        return metaKey;
    }

    public String getMetaValue() {
        return metaValue;
    }
}
