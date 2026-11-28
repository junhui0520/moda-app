package com.moda.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: {Base URL}/swagger-ui/index.html, OpenAPI JSON: {Base URL}/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI modaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Moda API")
                .version("v1 (docs/api-spec.md v1.2)")
                .description("비로그인 게스트 세션 기반 얼굴형·체형 분석, 맞춤 유형·헤어·패션 추천, 챗봇 API"));
    }
}
