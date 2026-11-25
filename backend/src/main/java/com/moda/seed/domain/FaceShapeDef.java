package com.moda.seed.domain;

import com.moda.common.enums.FaceShape;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 얼굴형 6종 라벨·설명 (docs/style-rules.md 1.1, 6.1). */
@Entity
@Table(name = "face_shape_def")
public class FaceShapeDef {

    @Id
    @Column(name = "face_key", length = 30)
    private String faceKey;

    @Column(name = "label", nullable = false, length = 30)
    private String label;

    @Column(name = "face_index", nullable = false)
    private int faceIndex;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    protected FaceShapeDef() {
    }

    public FaceShapeDef(String faceKey) {
        this.faceKey = faceKey;
    }

    public void update(String label, int faceIndex, String description) {
        this.label = label;
        this.faceIndex = faceIndex;
        this.description = description;
    }

    public String getFaceKey() {
        return faceKey;
    }

    public FaceShape getFaceShape() {
        return FaceShape.fromKey(faceKey);
    }

    public String getLabel() {
        return label;
    }

    public int getFaceIndex() {
        return faceIndex;
    }

    public String getDescription() {
        return description;
    }
}
