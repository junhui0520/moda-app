package com.moda.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moda.chat.repository.ChatMessageRepository;
import com.moda.session.domain.GuestSession;
import com.moda.session.repository.GuestSessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 챗봇 테스트 공통: 세션 준비(① → ② → ③ → ④) · 호출 · JSON 검사 헬퍼. */
public abstract class ChatTestSupport {

    protected static final String BASE = "/api/v1/sessions";
    /** api-spec.md 예시 체험자 (TYPE-08). 몸무게 63 → 프롬프트·답변에 노출되면 안 됨 */
    protected static final String PROFILE = """
            {"age":21,"heightCm":174,"weightKg":63,"hairThickness":"THIN","hairCurl":"SEMI_CURLY","hairDensity":"MEDIUM"}""";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected GuestSessionRepository sessionRepository;
    @Autowired protected ChatMessageRepository chatRepository;

    protected String newSession() throws Exception {
        return data(call(json(post(BASE + "/guest"), "{\"photoConsent\":true}"), 201)).get("sessionId").asText();
    }

    protected String profiledSession() throws Exception {
        String sid = newSession();
        call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE), 200);
        return sid;
    }

    /** 얼굴(heart)·체형(small_inv_triangle) 분석까지 끝난 세션 → TYPE-08. */
    protected String readySession() throws Exception {
        String sid = profiledSession();
        call(multipart(BASE + "/" + sid + "/analyze/face").file(jpeg()).header("X-Mock-Face-Shape", "heart"), 200);
        call(multipart(BASE + "/" + sid + "/analyze/body").file(jpeg()).header("X-Mock-Body-Type", "small_inv_triangle"), 200);
        return sid;
    }

    protected static MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    protected MockHttpServletRequestBuilder chatGet(String sid) {
        return get(BASE + "/" + sid + "/chat/messages");
    }

    protected MockHttpServletRequestBuilder chatPost(String sid, String message) {
        return json(post(BASE + "/" + sid + "/chat/messages"), "{\"message\":" + JsonNodeFactory.instance.textNode(message) + "}");
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as("HTTP status — body: %s", content).isEqualTo(expectedStatus);
        return objectMapper.readTree(content);
    }

    protected static JsonNode data(JsonNode root) {
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.has("error")).isFalse();
        return root.get("data");
    }

    protected static JsonNode assertError(JsonNode root, String code) {
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.has("data")).isFalse();
        JsonNode err = root.get("error");
        assertThat(err.get("code").asText()).isEqualTo(code);
        return err;
    }

    protected static List<String> texts(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.asText()));
        return list;
    }

    protected long chatCount(String sid) {
        return chatRepository.countBySession(sid);
    }

    protected Instant expiresAt(String sid) {
        return sessionRepository.findById(sid).orElseThrow().getExpiresAt();
    }

    protected void setExpiresAt(String sid, Instant value) {
        GuestSession s = sessionRepository.findById(sid).orElseThrow();
        s.renewExpiry(value);
        sessionRepository.save(s);
    }
}
