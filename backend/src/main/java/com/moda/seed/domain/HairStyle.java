package com.moda.seed.domain;

import com.moda.common.enums.HairStyleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 헤어스타일 5종 (docs/style-rules.md 1.5). */
@Entity
@Table(name = "hair_style")
public class HairStyle {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "image_key", nullable = false, length = 50)
    private String imageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected HairStyle() {
    }

    public HairStyle(String code) {
        this.code = code;
    }

    public void update(String name, String description, String imageKey, int sortOrder) {
        this.name = name;
        this.description = description;
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public HairStyleCode getStyleCode() {
        return HairStyleCode.valueOf(code);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageKey() {
        return imageKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
