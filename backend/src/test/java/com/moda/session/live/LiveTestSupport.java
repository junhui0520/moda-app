package com.moda.session.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.session.domain.GuestSession;
import com.moda.session.repository.GuestSessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** ③-L·④-L 테스트 공통 헬퍼. */
abstract class LiveTestSupport {

    static final String BASE = "/api/v1/sessions";
    static final String PROFILE = """
            {"age":21,"heightCm":174,"weightKg":63,"hairThickness":"THIN","hairCurl":"SEMI_CURLY","hairDensity":"MEDIUM"}""";

    /** 측정 페이지 payload 예시: 2회차 × 프레임 3장 + 촬영 품질. */
    static final String FACE_PAYLOAD = """
            {"rounds":[
               [{"length_ratio":1.18,"forehead_ratio":0.93,"jaw_ratio":0.69,"chin_ratio":0.62,"jaw_angle_deg":141.5},
                {"length_ratio":1.17,"forehead_ratio":0.94,"jaw_ratio":0.70,"chin_ratio":0.61,"jaw_angle_deg":141.0},
                {"length_ratio":1.19,"forehead_ratio":0.92,"jaw_ratio":null,"chin_ratio":0.62,"jaw_angle_deg":142.0}],
               [{"length_ratio":1.18,"forehead_ratio":0.93,"jaw_ratio":0.69,"chin_ratio":0.63,"jaw_angle_deg":141.2}]
             ],
             "quality":{"yaw":1.2,"pitch":3.4}}""";
    static final String BODY_PAYLOAD = """
            {"rounds":[[{"shoulder_ratio":0.282,"waist_ratio":0.150,"hip_ratio":0.187}],
                       [{"shoulder_ratio":0.283,"waist_ratio":0.151,"hip_ratio":0.186}]],
             "quality":{"min_visibility":0.91}}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GuestSessionRepository sessionRepository;

    String newSession() throws Exception {
        return data(call(json(post(BASE + "/guest"), "{\"photoConsent\":true}"), 201)).get("sessionId").asText();
    }

    String profiledSession() throws Exception {
        String sid = newSession();
        call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE), 200);
        return sid;
    }

    static MockHttpServletRequestBuilder faceLive(String sid, String body) {
        return json(post(BASE + "/" + sid + "/analyze/face/live"), body);
    }

    static MockHttpServletRequestBuilder bodyLive(String sid, String body) {
        return json(post(BASE + "/" + sid + "/analyze/body/live"), body);
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as("HTTP status — body: %s", content).isEqualTo(expectedStatus);
        return objectMapper.readTree(content);
    }

    static JsonNode data(JsonNode root) {
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.has("error")).isFalse();
        return root.get("data");
    }

    static JsonNode assertError(JsonNode root, String code) {
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.has("data")).isFalse();
        JsonNode err = root.get("error");
        assertThat(err.get("code").asText()).isEqualTo(code);
        return err;
    }

    static void assertProgress(JsonNode d, boolean profile, boolean face, boolean body) {
        JsonNode p = d.get("progress");
        assertThat(new boolean[]{p.get("profileInput").asBoolean(), p.get("faceAnalysis").asBoolean(), p.get("bodyAnalysis").asBoolean()})
                .containsExactly(profile, face, body);
    }

    void setExpiresAt(String sid, Instant value) {
        GuestSession s = sessionRepository.findById(sid).orElseThrow();
        s.renewExpiry(value);
        sessionRepository.save(s);
    }
}
