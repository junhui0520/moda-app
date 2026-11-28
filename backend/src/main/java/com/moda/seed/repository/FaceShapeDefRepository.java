package com.moda.seed.repository;

import com.moda.seed.domain.FaceShapeDef;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceShapeDefRepository extends JpaRepository<FaceShapeDef, String> {

    List<FaceShapeDef> findAllByOrderByFaceIndexAsc();
}
