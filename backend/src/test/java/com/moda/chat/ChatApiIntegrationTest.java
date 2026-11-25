package com.moda.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.chat.client.ChatClient;
import com.moda.chat.client.ChatClient.ChatCompletionCommand;
import com.moda.chat.service.ChatPromptBuilder;
import com.moda.common.enums.ChatRole;
import com.moda.session.service.SessionCleanupScheduler;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 챗봇 API ⑨·⑩ 통합 테스트 (moda.chat.mode=mock, H2).
 * {@link ChatClient} 빈을 스파이로 감싸 매 턴 실제로 전달된 시스템 프롬프트를 검사합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatApiIntegrationTest extends ChatTestSupport {

    static final String WELCOME_TYPE_08 = "안녕하세요! Moda 스타일 챗봇이에요. 분석 결과 'V라인 프레시' 유형(하트형 · 작은 역삼각형)으로 나왔어요. "
            + "추천받은 가르마 스타일이나 댄디 룩 연출법, 상황별 코디가 궁금하면 편하게 물어보세요.";
    static final List<String> PROMPTS_TYPE_08 = List.of(
            "가르마 스타일은 어떻게 연출하면 좋을까요?", "댄디 룩 기본 아이템 조합을 알려 줘", "하트형 얼굴에 어울리는 안경테는?", "소개팅 코디를 추천해 줘");

    @MockitoSpyBean ChatClient chatClient;
    @Autowired SessionCleanupScheduler cleanupScheduler;
    @Autowired JdbcTemplate jdbc;

    // ==================================================================================== ⑨ 진입

    @Test
    @DisplayName("⑨ 첫 진입: 맞춤 첫 인사 · 추천 질문 칩 4개 · messages [] · remainingTurns 20 (api-spec.md ⑨ 예시와 동일), TTL 미연장")
    void entryView() throws Exception {
        String sid = readySession();
        Instant before = expiresAt(sid);

        JsonNode d = data(call(chatGet(sid), 200));
        assertThat(d.get("sessionId").asText()).isEqualTo(sid);
        assertThat(d.get("welcomeMessage").asText()).isEqualTo(WELCOME_TYPE_08);
        assertThat(texts(d.get("suggestedPrompts"))).containsExactlyElementsOf(PROMPTS_TYPE_08);
        assertThat(d.get("messages")).isEmpty();
        assertThat(d.get("remainingTurns").asInt()).isEqualTo(20);
        assertThat(d.get("maxTurns").asInt()).isEqualTo(20);

        assertThat(expiresAt(sid)).as("⑨ 조회는 TTL을 연장하지 않는다").isEqualTo(before);
        assertThat(OffsetDateTime.parse(d.get("expiresAt").asText()).toInstant()).isEqualTo(before);
        assertThat(d.get("expiresAt").asText()).endsWith("+09:00");
        assertThat(chatCount(sid)).as("welcomeMessage는 저장하지 않는다").isZero();
    }

    // ==================================================================================== ⑩ 대화

    @Test
    @DisplayName("⑩ 정상 대화: trim 저장 · USER/ASSISTANT 쌍 저장 · 턴 차감 · TTL 30분 연장 · 후속 추천 질문")
    void sendMessage() throws Exception {
        String sid = readySession();
        setExpiresAt(sid, Instant.now().plus(Duration.ofMinutes(5)));                 // 연장 여부를 분명히 보기 위해 만료 5분 전으로

        JsonNode d = data(call(chatPost(sid, "  가르마 스타일은 어떻게 연출하면 좋을까요?  "), 200));
        assertThat(d.get("userMessage").get("role").asText()).isEqualTo("USER");
        assertThat(d.get("userMessage").get("content").asText()).isEqualTo("가르마 스타일은 어떻게 연출하면 좋을까요?");
        assertThat(UUID.fromString(d.get("userMessage").get("messageId").asText())).isNotNull();
        JsonNode assistant = d.get("assistantMessage");
        assertThat(assistant.get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(assistant.get("content").asText()).contains("하트형").contains("가르마 스타일");
        assertThat(sentences(assistant.get("content").asText())).isBetween(3, 5);
        assertThat(d.get("remainingTurns").asInt()).isEqualTo(19);
        assertThat(d.get("maxTurns").asInt()).isEqualTo(20);
        assertThat(d.get("suggestedPrompts")).hasSizeBetween(3, 4);
        assertThat(texts(d.get("suggestedPrompts"))).doesNotContain("가르마 스타일은 어떻게 연출하면 좋을까요?");

        Instant extended = OffsetDateTime.parse(d.get("expiresAt").asText()).toInstant();
        assertThat(extended).isAfter(Instant.now().plus(Duration.ofMinutes(29)));
        assertThat(expiresAt(sid)).isEqualTo(extended);

        JsonNode h = data(call(chatGet(sid), 200));
        assertThat(h.get("messages")).hasSize(2);
        assertThat(h.get("messages").findValuesAsText("role")).containsExactly("USER", "ASSISTANT");
        assertThat(h.get("messages").get(1).get("content").asText()).isEqualTo(assistant.get("content").asText());
        assertThat(h.get("remainingTurns").asInt()).isEqualTo(19);
        assertThat(h.get("suggestedPrompts")).hasSizeBetween(3, 4);
        assertThat(chatCount(sid)).isEqualTo(2);
    }

    @Test
    @DisplayName("시스템 프롬프트: 가드레일 포함 · 몸무게/BMI 값 미포함 · ⑦ PATCH 보정값이 다음 턴에 즉시 반영 · 이전 대화 전달")
    void systemPromptReflectsLatestProfile() throws Exception {
        String sid = readySession();
        call(chatPost(sid, "헤어 추천해 줘"), 200);
        ChatCompletionCommand first = lastCommand();

        String prompt = first.systemPrompt();
        assertThat(prompt)
                .contains("V라인 프레시 (TYPE-08)")
                .contains("얼굴형: 하트형 (AI 분석)")
                .contains("AI 분석 특징 요약: 평균 대비 이마 너비 비율이 높고")         // faceDetail.aiReason
                .contains("체형: 작은 역삼각형 (AI 분석)")
                .contains("나이 21세, 키 174cm, 모발 굵기 가는 편, 곱슬 정도 반곱슬, 숱 보통")
                .contains("1. 가르마 스타일").contains("2. 쉐도우 스타일")
                .contains("1. 댄디").contains("추천 아이템: 블레이저, 셔츠, 니트 폴로, 슬랙스, 로퍼")
                // 가드레일
                .contains("3~5문장 이내")
                .contains("남성 헤어·패션·그루밍")
                .contains("몸무게·체중·BMI·체지방 등 체중 관련 수치는 절대 언급하거나 추정하지 않는다")
                .contains(String.join(", ", ChatPromptBuilder.FORBIDDEN_EXPRESSIONS))
                .contains("각진 → 또렷한");
        assertNoBodyMetricValues(prompt);
        assertThat(first.history()).isEmpty();

        // ⑦ 얼굴형 보정 + 곱슬 변경 → 다음 턴 프롬프트에 즉시 반영
        call(json(patch(BASE + "/" + sid + "/profile"), "{\"faceShape\":\"diamond\",\"hairCurl\":\"CURLY\"}"), 200);
        call(chatPost(sid, "그럼 지금 어울리는 헤어는?"), 200);
        ChatCompletionCommand second = lastCommand();
        assertThat(second.systemPrompt())
                .contains("V라인 샤프 (TYPE-07)")
                .contains("얼굴형: 다이아몬드형 (사용자가 AI 결과를 직접 수정)")
                .contains("곱슬 정도 곱슬")
                .doesNotContain("하트형 (AI 분석)")
                .doesNotContain("AI 분석 특징 요약");                                    // CORRECTED: AI 원본 문장은 제외
        assertNoBodyMetricValues(second.systemPrompt());
        assertThat(second.history()).extracting(ChatClient.ChatTurn::role).containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        assertThat(second.history().get(0).content()).isEqualTo("헤어 추천해 줘");
        assertThat(second.userMessage()).isEqualTo("그럼 지금 어울리는 헤어는?");

        JsonNode h = data(call(chatGet(sid), 200));
        assertThat(h.get("welcomeMessage").asText()).contains("'V라인 샤프' 유형(다이아몬드형 · 작은 역삼각형)");
    }

    @Test
    @DisplayName("20턴 사용 후 remainingTurns 0 · 추천 칩 [] → 21번째 전송은 429 CHAT_TURN_LIMIT_EXCEEDED (meta 포함, 미저장)")
    void turnLimit() throws Exception {
        String sid = readySession();
        JsonNode last = null;
        for (int i = 1; i <= 20; i++) {
            last = data(call(chatPost(sid, "코디 추천 " + i), 200));
            assertThat(last.get("remainingTurns").asInt()).isEqualTo(20 - i);
        }
        assertThat(last.get("suggestedPrompts")).isEmpty();
        assertThat(chatCount(sid)).isEqualTo(40);

        JsonNode err = assertError(call(chatPost(sid, "한 번 더"), 429), "CHAT_TURN_LIMIT_EXCEEDED");
        assertThat(err.get("status").asInt()).isEqualTo(429);
        assertThat(err.get("message").asText()).isEqualTo("이번 체험에서 대화할 수 있는 횟수(20회)를 모두 사용했어요.");
        assertThat(err.get("meta").get("maxTurns").asInt()).isEqualTo(20);
        assertThat(err.get("meta").get("usedTurns").asInt()).isEqualTo(20);
        assertThat(err.get("meta").get("remainingTurns").asInt()).isZero();
        assertThat(chatCount(sid)).isEqualTo(40);

        JsonNode h = data(call(chatGet(sid), 200));
        assertThat(h.get("remainingTurns").asInt()).isZero();
        assertThat(h.get("suggestedPrompts")).isEmpty();
        assertThat(h.get("messages")).hasSize(40);
        List<String> roles = h.get("messages").findValuesAsText("role");
        assertThat(roles.subList(0, 4)).containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
    }

    @Test
    @DisplayName("같은 세션 동시 전송은 순차 처리: 둘 다 성공, 턴 2개 차감, 순번 중복 없음")
    void concurrentSendsAreSerialized() throws Exception {
        String sid = readySession();
        CompletableFuture<MvcResult> a = CompletableFuture.supplyAsync(() -> perform(sid, "헤어 질문 A"));
        CompletableFuture<MvcResult> b = CompletableFuture.supplyAsync(() -> perform(sid, "헤어 질문 B"));
        Set<Integer> remaining = Set.of(remainingOf(a.get()), remainingOf(b.get()));
        assertThat(remaining).containsExactlyInAnyOrder(19, 18);
        assertThat(chatCount(sid)).isEqualTo(4);
        assertThat(chatRepository.findHistory(sid)).extracting(m -> m.getSequenceNo()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Mock 답변: 주제별 개인화, 3~5문장, R-11 금지 표현·체중 수치 없음 (체중 질문은 수치 대신 핏 안내)")
    void mockRepliesFollowGuardrails() throws Exception {
        String sid = readySession();
        List<String> questions = List.of("제 몸무게랑 BMI가 어느 정도인지 알려 주세요", "하트형 얼굴에 어울리는 안경테는?",
                "소개팅 코디를 추천해 줘", "면도 후 피부 관리 팁은?", "안녕하세요");
        for (String q : questions) {
            String reply = data(call(chatPost(sid, q), 200)).get("assistantMessage").get("content").asText();
            assertThat(sentences(reply)).as(q).isBetween(3, 5);
            assertThat(ChatPromptBuilder.FORBIDDEN_EXPRESSIONS).as(q).noneMatch(reply::contains);
            assertNoBodyMetricValues(reply);
            switch (q.substring(0, 2)) {
                case "제 " -> assertThat(reply).contains("수치는 따로 안내하지 않고").contains("작은 역삼각형 체형");
                case "하트" -> assertThat(reply).contains("안경테");
                case "소개" -> assertThat(reply).contains("소개팅이나 데이트라면 블레이저에 슬랙스를 매치해");
                case "면도" -> assertThat(reply).contains("선크림");
                default -> assertThat(reply).contains("'V라인 프레시' 유형");
            }
        }
    }

    // ==================================================================================== 예외

    @Nested
    @DisplayName("예외")
    class Errors {

        @Test
        @DisplayName("409: 기본 정보 전 PROFILE_INPUT_REQUIRED → 분석 미완료 ANALYSIS_INCOMPLETE(meta)")
        void preconditions() throws Exception {
            String fresh = newSession();
            assertError(call(chatGet(fresh), 409), "PROFILE_INPUT_REQUIRED");
            assertError(call(chatPost(fresh, "안녕"), 409), "PROFILE_INPUT_REQUIRED");

            String profiled = profiledSession();
            JsonNode both = assertError(call(chatGet(profiled), 409), "ANALYSIS_INCOMPLETE");
            assertThat(texts(both.get("meta").get("missing"))).containsExactly("FACE", "BODY");
            assertThat(both.get("meta").get("nextStep").asText()).isEqualTo("FACE_ANALYSIS");

            call(multipart(BASE + "/" + profiled + "/analyze/face").file(jpeg()), 200);
            JsonNode body = assertError(call(chatPost(profiled, "안녕"), 409), "ANALYSIS_INCOMPLETE");
            assertThat(texts(body.get("meta").get("missing"))).containsExactly("BODY");
            assertThat(chatCount(profiled)).isZero();
        }

        @Test
        @DisplayName("404 SESSION_NOT_FOUND / 410 SESSION_EXPIRED")
        void notFoundAndExpired() throws Exception {
            assertError(call(chatGet(UUID.randomUUID().toString()), 404), "SESSION_NOT_FOUND");
            String sid = readySession();
            setExpiresAt(sid, Instant.now().minusSeconds(5));
            assertError(call(chatGet(sid), 410), "SESSION_EXPIRED");
            assertError(call(chatPost(sid, "안녕"), 410), "SESSION_EXPIRED");
        }

        @Test
        @DisplayName("400 INVALID_REQUEST: 공백만 · 500자 초과 · message 누락 · 본문 없음 (미저장)")
        void validation() throws Exception {
            String sid = readySession();
            JsonNode blank = assertError(call(chatPost(sid, "   \n  "), 400), "INVALID_REQUEST");
            assertThat(blank.get("details").get(0).get("field").asText()).isEqualTo("message");
            assertThat(blank.get("details").get(0).get("reason").asText()).isEqualTo("공백을 제외하고 1자 이상 500자 이하로 입력해 주세요.");
            assertError(call(chatPost(sid, "가".repeat(501)), 400), "INVALID_REQUEST");
            assertError(call(json(post(BASE + "/" + sid + "/chat/messages"), "{}"), 400), "INVALID_REQUEST");
            assertError(call(post(BASE + "/" + sid + "/chat/messages"), 400), "INVALID_REQUEST");
            assertError(call(chatPost(sid, "헤어").header("X-Mock-Delay-Ms", "abc"), 400), "INVALID_REQUEST");
            assertThat(chatCount(sid)).isZero();
            data(call(chatPost(sid, "  " + "가".repeat(500) + "  "), 200));             // trim 후 정확히 500자는 허용
        }
    }

    // ==================================================================================== 연쇄 삭제

    @Nested
    @DisplayName("chat_message 연쇄 삭제")
    class Cascade {

        @Test
        @DisplayName("⑧ DELETE: 대화 내역 즉시 삭제, ⑧ 응답 구조는 변경 없음")
        void deleteSessionPurgesMessages() throws Exception {
            String sid = readySession();
            call(chatPost(sid, "헤어 추천"), 200);
            call(chatPost(sid, "코디 추천"), 200);
            assertThat(chatCount(sid)).isEqualTo(4);

            JsonNode d = data(call(delete(BASE + "/" + sid), 200));
            assertThat(d.fieldNames()).toIterable()
                    .containsExactly("sessionId", "deleted", "alreadyDeleted", "deletedPhotoCount", "deletedAt");
            assertThat(chatCount(sid)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from chat_message where session_id = ?", Long.class, sid)).isZero();
        }

        @Test
        @DisplayName("만료 세션 정리 스케줄러: 대화 내역도 함께 삭제")
        void schedulerPurgesMessages() throws Exception {
            String sid = readySession();
            call(chatPost(sid, "헤어 추천"), 200);
            String alive = readySession();
            call(chatPost(alive, "헤어 추천"), 200);
            setExpiresAt(sid, Instant.now().minusSeconds(5));

            assertThat(cleanupScheduler.cleanupExpiredSessions()).isGreaterThanOrEqualTo(1);
            assertThat(sessionRepository.findById(sid)).isEmpty();
            assertThat(chatCount(sid)).isZero();
            assertThat(chatCount(alive)).as("만료되지 않은 세션의 대화는 유지").isEqualTo(2);
        }

        @Test
        @DisplayName("DB FK ON DELETE CASCADE: 세션 행만 직접 삭제해도 chat_message가 함께 삭제된다")
        void databaseLevelCascade() throws Exception {
            String sid = readySession();
            call(chatPost(sid, "헤어 추천"), 200);
            assertThat(jdbc.queryForObject("select count(*) from chat_message where session_id = ?", Long.class, sid)).isEqualTo(2);
            jdbc.update("delete from guest_session where session_id = ?", sid);
            assertThat(jdbc.queryForObject("select count(*) from chat_message where session_id = ?", Long.class, sid)).isZero();
        }
    }

    // ==================================================================================== helpers

    private ChatCompletionCommand lastCommand() {
        ArgumentCaptor<ChatCompletionCommand> captor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        verify(chatClient, atLeastOnce()).complete(captor.capture());
        List<ChatCompletionCommand> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    /** 몸무게(63)·BMI(20.8)·구간명·kg 표기가 없어야 함. */
    static void assertNoBodyMetricValues(String text) {
        assertThat(text).doesNotContain("63").doesNotContain("20.8").doesNotContain("BMI_")
                .doesNotContain("weightKg").doesNotContainIgnoringCase("kg").doesNotContain("HIDDEN");
    }

    static int sentences(String text) {
        Matcher m = Pattern.compile("[.!?](\\s|$)").matcher(text.strip());
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private MvcResult perform(String sid, String message) {
        try {
            return mvc.perform(chatPost(sid, message).header("X-Mock-Delay-Ms", "300")).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int remainingOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").get("remainingTurns").asInt();
    }
}
