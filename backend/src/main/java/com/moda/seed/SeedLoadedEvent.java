package com.moda.seed;

/** 시드 upsert가 끝났음을 알리는 이벤트. 시드 기반 캐시({@link com.moda.seed.catalog.SeedCatalog})를 무효화합니다. */
public record SeedLoadedEvent(SeedLoadResult result) {
}
