package com.moda.global.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/health → 200, 공통 Envelope, status ok / aiMode mock / chatMode mock")
    void health() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/health")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.toString()).isEqualTo("{\"success\":true,\"data\":{\"status\":\"ok\",\"aiMode\":\"mock\",\"chatMode\":\"mock\"}}");
    }

    @Test
    @DisplayName("CORS: 임의 Origin에서 헬스체크 호출 허용")
    void healthCors() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/health").header("Origin", "https://example.app")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNotNull();
    }
}
