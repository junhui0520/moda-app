package com.moda.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.global.config.ModaProperties;
import com.moda.seed.dto.AnalysisLabelsSeed;
import com.moda.seed.dto.CatalogSeed;
import com.moda.seed.dto.RuleSeed;
import com.moda.seed.dto.SeedBundle;
import com.moda.seed.dto.TypeDefinitionsSeed;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * {@code moda.seed.location}(기본 {@code classpath:seed/})에서 시드 JSON 5종을 읽습니다.
 * <p>정의되지 않은 필드가 있으면 오타로 보고 실패시킵니다.
 */
@Component
public class SeedReader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String location;

    @Autowired
    public SeedReader(ObjectMapper objectMapper, ResourceLoader resourceLoader, ModaProperties properties) {
        this(objectMapper, resourceLoader, properties.seed().location());
    }

    public SeedReader(ObjectMapper objectMapper, ResourceLoader resourceLoader, String location) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.resourceLoader = resourceLoader;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    public SeedBundle read() {
        return new SeedBundle(
                read(SeedFile.TYPE_DEFINITIONS, TypeDefinitionsSeed.class),
                read(SeedFile.CATALOG, CatalogSeed.class),
                read(SeedFile.HAIR_RULES, RuleSeed.class),
                read(SeedFile.FASHION_RULES, RuleSeed.class),
                read(SeedFile.ANALYSIS_LABELS, AnalysisLabelsSeed.class));
    }

    private <T> T read(SeedFile file, Class<T> type) {
        String path = location + file.fileName();
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new SeedValidationException("[" + file.fileName() + "] 시드 파일이 없습니다: " + path, null);
        }
        try (InputStream in = resource.getInputStream()) {
            T value = objectMapper.readValue(in, type);
            if (value == null) {
                throw new SeedValidationException("[" + file.fileName() + "] 시드 파일이 비어 있습니다: " + path, null);
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new SeedValidationException("[" + file.fileName() + "] JSON 파싱 실패 (line "
                    + (e.getLocation() == null ? "?" : e.getLocation().getLineNr()) + "): " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new SeedValidationException("[" + file.fileName() + "] 시드 파일 읽기 실패: " + e.getMessage(), e);
        }
    }
}
