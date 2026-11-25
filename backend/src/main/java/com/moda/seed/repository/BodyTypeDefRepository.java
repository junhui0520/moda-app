package com.moda.seed.repository;

import com.moda.seed.domain.BodyTypeDef;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyTypeDefRepository extends JpaRepository<BodyTypeDef, String> {

    List<BodyTypeDef> findAllByOrderByBodyIndexAsc();
}
