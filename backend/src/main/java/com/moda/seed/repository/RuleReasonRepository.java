package com.moda.seed.repository;

import com.moda.seed.domain.ReasonType;
import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.RuleReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleReasonRepository extends JpaRepository<RuleReason, String> {

    List<RuleReason> findAllByRuleDomain(RuleDomain ruleDomain);

    long countByRuleDomainAndReasonType(RuleDomain ruleDomain, ReasonType reasonType);
}
