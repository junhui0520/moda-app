package com.moda.seed.repository;

import com.moda.seed.domain.HairStyle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HairStyleRepository extends JpaRepository<HairStyle, String> {

    List<HairStyle> findAllByOrderBySortOrderAsc();
}
