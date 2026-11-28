package com.moda.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moda.ModaApplication;
import com.moda.seed.repository.AnalysisLabelRepository;
import com.moda.seed.repository.TypeDefinitionRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 실제 애플리케이션 기동 단위로 SeedLoader 동작을 확인합니다.
 */
class SeedLoaderStartupTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dir;

    @Test
    @DisplayName("시드 검증 실패 시 서버 기동이 중단된다")
    void invalidSeedAbortsStartup() throws IOException {
        SeedValidatorTest.copySeedFiles(dir);
        ObjectNode root = (ObjectNode) MAPPER.readTree(dir.resolve("type-definitions.json").toFile());
        root.withArray("types").remove(0); // TYPE-01 삭제 → 23개
        MAPPER.writeValue(dir.resolve("type-definitions.json").toFile(), root);

        // SmartInitializingSingleton 단계에서 던진 예외가 SpringApplication.run() 밖으로 그대로 전파되어 기동이 중단됨
        assertThatThrownBy(() -> start("moda.seed.location=" + dir.toUri()).close())
                .isInstanceOf(SeedValidationException.class)
                .hasMessageContaining("유형 개수는 24개")
                .hasMessageContaining("TYPE-01");
    }

    @Test
    @DisplayName("moda.seed.mode=none 이면 적재를 건너뛴다")
    void noneModeSkipsLoading() {
        try (ConfigurableApplicationContext ctx = start("moda.seed.mode=none")) {
            assertThat(ctx.getBean(TypeDefinitionRepository.class).count()).isZero();
            assertThat(ctx.getBean(AnalysisLabelRepository.class).count()).isZero();
            assertThat(ctx.getBean(SeedLoader.class).load()).isEmpty();
        }
    }

    @Test
    @DisplayName("정상 시드로 기동하면 적재 완료 상태로 컨텍스트가 뜬다")
    void validSeedStarts() {
        try (ConfigurableApplicationContext ctx = start()) {
            assertThat(ctx.getBean(TypeDefinitionRepository.class).count()).isEqualTo(24);
        }
    }

    /**
     * 설정은 커맨드라인 인자({@code --key=value})로 전달합니다.
     * ({@code SpringApplicationBuilder.properties()}는 최하위 우선순위 기본값이라 application.yml에 덮입니다.)
     */
    private ConfigurableApplicationContext start(String... extraProperties) {
        List<String> args = new ArrayList<>(List.of(
                "spring.datasource.url=jdbc:h2:mem:startup-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "spring.h2.console.enabled=false",
                "spring.main.banner-mode=off"));
        args.addAll(List.of(extraProperties));
        return new SpringApplicationBuilder(ModaApplication.class)
                .web(WebApplicationType.NONE)
                .run(args.stream().map(a -> "--" + a).toArray(String[]::new));
    }
}
