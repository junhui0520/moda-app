package com.moda.chat.service;

import com.moda.chat.model.ChatContext;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 답변 후처리 — 프롬프트 가드레일이 지켜지지 않았을 때의 마지막 안전망.
 * <ul>
 *   <li>체중·BMI 수치(예: {@code 63kg}, {@code BMI 20.8}, {@code 체중 70})가 들어 있으면 답변 전체를 안전한 안내 문구로 대체 (R-11)</li>
 *   <li>마크다운 강조·제목 기호 제거 (일반 텍스트 응답, api-spec.md ⑨·⑩ 공통 규칙)</li>
 * </ul>
 */
@Component
public class ChatReplySanitizer {

    private static final Logger log = LoggerFactory.getLogger(ChatReplySanitizer.class);

    static final Pattern BODY_METRIC_NUMBER = Pattern.compile(
            "(?i)(bmi\\s*(지수)?\\s*[:=은는이가]?\\s*\\d)|(\\d+(\\.\\d+)?\\s*(kg|㎏|킬로))|((몸무게|체중)\\s*[:=은는이가]?\\s*(약\\s*)?\\d)");

    public String sanitize(String reply, ChatContext ctx) {
        String text = reply == null ? "" : reply.strip();
        if (BODY_METRIC_NUMBER.matcher(text).find()) {
            log.warn("[Chat] 답변에 체중·BMI 수치가 포함되어 안전 문구로 대체했습니다.");
            return bodyMetricFallback(ctx);
        }
        return text
                .replaceAll("\\*\\*|__", "")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .strip();
    }

    /** 체중·BMI 수치 대신 핏·실루엣 관점으로 안내하는 문구 (Mock 체중 질문 답변과 공용). */
    public static String bodyMetricFallback(ChatContext ctx) {
        return "체중이나 BMI 같은 수치는 따로 안내하지 않고, 체형에 어울리는 핏 중심으로 도와드릴게요. "
                + ctx.bodyPhrase() + "에는 " + ctx.topFashion().name() + " 스타일처럼 실루엣이 정돈된 아이템이 잘 어울려요. "
                + "상의는 어깨선에 맞는 사이즈, 하의는 스트레이트 핏을 기본으로 고르면 실패가 적어요. "
                + "궁금한 아이템이 있으면 편하게 물어보세요.";
    }
}
