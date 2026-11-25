package com.moda.seed.repository;

import com.moda.seed.domain.SeedMeta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedMetaRepository extends JpaRepository<SeedMeta, String> {
}
