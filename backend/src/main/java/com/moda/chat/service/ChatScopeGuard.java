package com.moda.chat.service;

import com.moda.chat.model.ChatContext;
import com.moda.chat.model.ChatTopic;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 챗봇이 <b>알 수 있는 질문</b>인지 LLM 호출 전에 거릅니다. 챗봇의 지식은 사용자 분석 결과, style-rules.md 추천 규칙, chat-knowledge.json뿐이므로,
 * 그 밖의 질문(자료에 없는 그루밍·날씨 등)은 지어내지 않고 "알 수 없다"고 답합니다. 걸러진 질문은 LLM을 호출하지 않고 고정 문구로 답합니다
 * (Mock·External 공통, 빠르고 항상 같음).
 * <ul>
 *   <li>인사·감사 → 짧은 인사 (분석 결과를 늘어놓지 않음)</li>
 *   <li>범위 안: 근거가 있는 질문(특정 스타일 이름 · chat-knowledge.json 항목 매칭), 분석 결과·추천·기준·어울림 질문, 패션·코디·소품, 체중 질문(수치 비노출 안내)</li>
 *   <li>그 외 → "알 수 없는 내용" 안내</li>
 * </ul>
 * shortcut: 키워드 기반이라 표현이 특이하면 범위 안 질문도 거절될 수 있음. 오거절이 잦으면 LLM 분류 호출로 교체.
 */
@Component
public class ChatScopeGuard {

    static final String GREETING_REPLY = "안녕하세요! Moda 스타일 챗봇이에요. 분석 결과나 추천 스타일, 어울리는 헤어·코디가 궁금하면 편하게 물어보세요.";
    static final String THANKS_REPLY = "도움이 되었다니 기뻐요. 더 궁금한 스타일이 있으면 편하게 물어보세요.";
    static final String OUT_OF_SCOPE_REPLY = "그 내용은 제가 알 수 없어요. 저는 분석 결과와 추천 스타일, 스타일 규칙에 맞는 헤어·패션을 안내해 드려요. "
            + "예를 들어 '가르마도 어울릴까?', '출근 코디 추천해 줘', '얼굴형은 어떻게 나눠?'처럼 물어봐 주세요.";

    private static final Pattern GREETING = Pattern.compile("(?i)^(안녕\\S*|안뇽\\S*|하이|헬로|hi|hello|ㅎㅇ|반가\\S*|처음 뵙겠습니다)[\\s!.~?]*$");
    private static final Pattern THANKS = Pattern.compile("(?i)^(고마\\S*|감사\\S*|땡큐|thanks?|ㄱㅅ)[\\s!.~?]*$");

    /** 이 단어가 있으면 분석 결과·추천·규칙에 관한 질문으로 봄 ('스타일' 단독은 '스타일링 팁'이 섞여서 제외). */
    private static final List<String> SCOPE_WORDS = List.of(
            "추천", "어울", "분석", "결과", "유형", "얼굴형", "체형", "기준", "나한테", "저한테", "맞는", "맞을", "판정");

    /** 고정 문구로 답해야 하면 그 문구, 아니면 비어 있음(= LLM·Mock 답변). */
    public Optional<String> shortReply(String message, ChatContext ctx, boolean grounded) {
        String m = message.strip();
        if (GREETING.matcher(m).matches()) {
            return Optional.of(GREETING_REPLY);
        }
        if (THANKS.matcher(m).matches()) {
            return Optional.of(THANKS_REPLY);
        }
        return inScope(m, ctx, grounded) ? Optional.empty() : Optional.of(OUT_OF_SCOPE_REPLY);
    }

    private static boolean inScope(String m, ChatContext ctx, boolean grounded) {
        if (grounded || ChatTopic.occasion(m) != null) {
            return true;
        }
        ChatTopic topic = ChatTopic.detect(m);
        if (topic == ChatTopic.FASHION || topic == ChatTopic.ACCESSORY || topic == ChatTopic.BODY_METRIC) {
            return true;
        }
        if (topic == ChatTopic.GROOMING) {
            return false;
        }
        return SCOPE_WORDS.stream().anyMatch(m::contains)
                || m.contains(ctx.faceShape().label()) || m.contains(ctx.bodyType().label()) || m.contains(ctx.typeName());
    }
}
