package com.moda.seed.repository;

import com.moda.seed.domain.AnalysisLabel;
import com.moda.seed.domain.LabelCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisLabelRepository extends JpaRepository<AnalysisLabel, Long> {

    List<AnalysisLabel> findAllByCategory(LabelCategory category);

    Optional<AnalysisLabel> findByCategoryAndLabelCode(LabelCategory category, String labelCode);

    long countByCategory(LabelCategory category);
}
