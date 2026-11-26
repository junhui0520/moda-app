package com.moda.image;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StyleImageControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("sql/ 적재 → 헤어 5종·패션 9종 모두 이미지 경로, 코디는 full_shot + 아이템, 경로의 파일이 실제로 서빙됨")
    void styleImages() throws Exception {
        String body = mvc.perform(get("/api/v1/style-images")).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");

        assertThat(data.size()).isEqualTo(14);
        assertThat(data.get("hair_up_style").get("url").asText()).isEqualTo("/images/hair/REGENT_CUT/01.png");
        JsonNode casual = data.get("fashion_casual");
        assertThat(casual.get("url").asText()).isEqualTo("/images/style/CASUAL/CASUAL_01/full_shot.png");
        assertThat(casual.get("items")).hasSize(5);
        assertThat(casual.get("items").get(0).get("label").asText()).isEqualTo("후드집업");

        for (JsonNode set : data) {
            assertThat(mvc.perform(get(set.get("url").asText())).andReturn().getResponse().getStatus())
                    .as(set.get("url").asText()).isEqualTo(200);
            for (JsonNode item : set.get("items")) {
                assertThat(mvc.perform(get(item.get("url").asText())).andReturn().getResponse().getStatus())
                        .as(item.get("url").asText()).isEqualTo(200);
            }
        }
    }
}
