package com.moda.seed.repository;

import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.RuleScore;
import com.moda.seed.domain.ScoreType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleScoreRepository extends JpaRepository<RuleScore, Long> {

    List<RuleScore> findAllByRuleDomain(RuleDomain ruleDomain);

    long countByRuleDomainAndScoreType(RuleDomain ruleDomain, ScoreType scoreType);

    Optional<RuleScore> findByRuleDomainAndScoreTypeAndFactorAndConditionValueAndStyleCode(
            RuleDomain ruleDomain, ScoreType scoreType, String factor, String conditionValue, String styleCode);
}
