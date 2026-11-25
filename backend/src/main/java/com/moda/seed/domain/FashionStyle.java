package com.moda.seed.domain;

import com.moda.common.enums.FashionStyleCode;
import com.moda.common.jpa.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/** 패션스타일 9종 (docs/style-rules.md 1.6). */
@Entity
@Table(name = "fashion_style")
public class FashionStyle {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /** 추천 아이템 키워드 5개 (순서 고정). */
    @Convert(converter = StringListConverter.class)
    @Column(name = "item_keywords", nullable = false, length = 1000)
    private List<String> itemKeywords;

    @Column(name = "image_key", nullable = false, length = 50)
    private String imageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected FashionStyle() {
    }

    public FashionStyle(String code) {
        this.code = code;
    }

    public void update(String name, String description, List<String> itemKeywords, String imageKey, int sortOrder) {
        this.name = name;
        this.description = description;
        this.itemKeywords = List.copyOf(itemKeywords);
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public FashionStyleCode getStyleCode() {
        return FashionStyleCode.valueOf(code);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getItemKeywords() {
        return itemKeywords;
    }

    public String getImageKey() {
        return imageKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
