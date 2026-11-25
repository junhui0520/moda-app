package com.moda.seed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 시드 upsert 결과: 테이블별 신규(inserted)·갱신(updated) 행 수. */
public record SeedLoadResult(String ruleVersion, Map<String, TableResult> tables) {

    public record TableResult(int inserted, int updated) {

        public int total() {
            return inserted + updated;
        }
    }

    public int totalInserted() {
        return tables.values().stream().mapToInt(TableResult::inserted).sum();
    }

    public int totalUpdated() {
        return tables.values().stream().mapToInt(TableResult::updated).sum();
    }

    public TableResult table(String tableName) {
        return tables.getOrDefault(tableName, new TableResult(0, 0));
    }

    public String summary() {
        return tables.entrySet().stream()
                .map(en -> en.getKey() + "=" + en.getValue().total() + "(+" + en.getValue().inserted() + ")")
                .collect(Collectors.joining(", "));
    }

    static final class Builder {
        private final String ruleVersion;
        private final Map<String, int[]> counts = new LinkedHashMap<>();

        Builder(String ruleVersion) {
            this.ruleVersion = ruleVersion;
        }

        void add(String table, boolean inserted) {
            int[] c = counts.computeIfAbsent(table, k -> new int[2]);
            c[inserted ? 0 : 1]++;
        }

        SeedLoadResult build() {
            Map<String, TableResult> tables = new LinkedHashMap<>();
            counts.forEach((k, v) -> tables.put(k, new TableResult(v[0], v[1])));
            return new SeedLoadResult(ruleVersion, Collections.unmodifiableMap(tables));
        }
    }
}
