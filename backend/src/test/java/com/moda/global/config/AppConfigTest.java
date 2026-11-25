package com.moda.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moda.global.config.ModaProperties.ClientMode;
import com.moda.global.config.ModaProperties.SeedMode;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppConfigTest {

    @Autowired ModaProperties properties;
    @Autowired MultipartProperties multipartProperties;
    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("moda.* 커스텀 프로퍼티 기본값 바인딩")
    void modaPropertiesDefaults() {
        assertThat(properties.seed().mode()).isEqualTo(SeedMode.UPSERT);
        assertThat(properties.seed().location()).isEqualTo("classpath:seed/");

        assertThat(properties.ai().mode()).isEqualTo(ClientMode.MOCK);
        assertThat(properties.ai().baseUrl()).isEqualTo("http://localhost:8000");
        assertThat(properties.ai().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.ai().faceReadTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.ai().bodyReadTimeout()).isEqualTo(Duration.ofSeconds(20));

        assertThat(properties.chat().mode()).isEqualTo(ClientMode.MOCK);
        assertThat(properties.chat().maxTurns()).isEqualTo(20);
        assertThat(properties.chat().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.chat().readTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.chat().toString()).doesNotContain("apiKey=sk");

        assertThat(properties.session().ttlMinutes()).isEqualTo(30);
        assertThat(properties.session().cleanupInterval()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Multipart 제한: 파일 10MB, 요청 12MB")
    void multipartLimits() {
        assertThat(multipartProperties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(multipartProperties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(12));
    }

    @Test
    @DisplayName("CORS: 임의 Origin의 PATCH preflight 허용")
    void corsPreflightAllowsAnyOrigin() throws Exception {
        mockMvc.perform(options("/v3/api-docs")
                        .header("Origin", "http://192.168.0.10:8081")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
    }

    @Test
    @DisplayName("Swagger(OpenAPI JSON) 노출")
    void openApiDocsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
