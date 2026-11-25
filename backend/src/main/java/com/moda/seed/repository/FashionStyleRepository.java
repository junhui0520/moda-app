package com.moda.seed.repository;

import com.moda.seed.domain.FashionStyle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FashionStyleRepository extends JpaRepository<FashionStyle, String> {

    List<FashionStyle> findAllByOrderBySortOrderAsc();
}
