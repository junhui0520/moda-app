package com.moda.seed.repository;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.seed.domain.TypeDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeDefinitionRepository extends JpaRepository<TypeDefinition, String> {

    Optional<TypeDefinition> findByBodyTypeAndFaceShape(BodyType bodyType, FaceShape faceShape);

    List<TypeDefinition> findAllByOrderBySortOrderAsc();
}
