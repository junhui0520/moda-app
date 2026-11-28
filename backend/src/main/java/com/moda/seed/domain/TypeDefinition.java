package com.moda.seed.domain;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.jpa.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;

/** 맞춤 유형 24종 (docs/style-rules.md 5.2). 코드(typeCode)와 텍스트를 분리해 시드에서만 텍스트를 관리합니다 (R-07). */
@Entity
@Table(name = "type_definition",
        uniqueConstraints = @UniqueConstraint(name = "uk_type_definition_body_face", columnNames = {"body_type", "face_shape"}))
public class TypeDefinition {

    @Id
    @Column(name = "type_code", length = 10)
    private String typeCode;

    @Column(name = "body_type", nullable = false, length = 30)
    private BodyType bodyType;

    @Column(name = "face_shape", nullable = false, length = 30)
    private FaceShape faceShape;

    @Column(name = "type_name", nullable = false, length = 50)
    private String typeName;

    @Column(name = "name_confirmed", nullable = false)
    private boolean nameConfirmed;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "style_points", nullable = false, length = 2000)
    private List<String> stylePoints;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected TypeDefinition() {
    }

    public TypeDefinition(String typeCode) {
        this.typeCode = typeCode;
    }

    public void update(BodyType bodyType, FaceShape faceShape, String typeName, boolean nameConfirmed,
                       String description, List<String> stylePoints, int sortOrder) {
        this.bodyType = bodyType;
        this.faceShape = faceShape;
        this.typeName = typeName;
        this.nameConfirmed = nameConfirmed;
        this.description = description;
        this.stylePoints = List.copyOf(stylePoints);
        this.sortOrder = sortOrder;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public FaceShape getFaceShape() {
        return faceShape;
    }

    public String getTypeName() {
        return typeName;
    }

    public boolean isNameConfirmed() {
        return nameConfirmed;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getStylePoints() {
        return stylePoints;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
