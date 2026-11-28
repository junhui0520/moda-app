package com.moda.chat.service;

import static com.moda.common.text.Josa.eulReul;
import static com.moda.common.text.Josa.eunNeun;
import static com.moda.common.text.Josa.iNa;

import com.moda.chat.model.ChatContext;
import com.moda.chat.model.ChatTopic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * LLM을 호출하지 않는 서버 템플릿 (api-spec.md ⑨): 맞춤 첫 인사({@code welcomeMessage})와 추천 질문 칩({@code suggestedPrompts}, 3~4개).
 * 세션의 맞춤 유형명·얼굴형·체형·1순위 추천 헤어·패션을 반영합니다. 첫 인사는 DB에 저장하지 않고 턴에도 포함하지 않습니다.
 */
@Component
public class ChatTemplates {

    public static final int MAX_PROMPTS = 4;
    public static final int MIN_PROMPTS = 3;

    public String welcome(ChatContext ctx) {
        return "안녕하세요! Moda 스타일 챗봇이에요. 분석 결과 '" + ctx.typeName() + "' 유형(" + ctx.faceShape().label() + " · "
                + ctx.bodyType().label() + ")으로 나왔어요. 추천받은 " + iNa(ctx.topHair().name()) + " "
                + ctx.topFashion().name() + " 룩 연출법, 상황별 코디가 궁금하면 편하게 물어보세요.";
    }

    /** 첫 진입(대화 없음): 결과 기반 4개. */
    public List<String> initialPrompts(ChatContext ctx) {
        return List.of(
                eunNeun(ctx.topHair().name()) + " 어떻게 연출하면 좋을까요?",
                ctx.topFashion().name() + " 룩 기본 아이템 조합을 알려 줘",
                ctx.faceShape().label() + " 얼굴에 어울리는 안경테는?",
                "소개팅 코디를 추천해 줘");
    }

    /**
     * 대화 이후: 마지막 사용자 질문의 주제 기준 후속 질문 3~4개. 이미 물어본 질문은 제외하고, 부족하면 첫 진입 칩으로 채웁니다.
     */
    public List<String> followUpPrompts(ChatContext ctx, String lastUserMessage, Collection<String> askedMessages) {
        String hair = ctx.topHair().name();
        String fashion = ctx.topFashion().name();
        List<String> candidates = switch (ChatTopic.detect(lastUserMessage)) {
            case HAIR -> List.of(
                    eunNeun(hair) + " 어떤 얼굴형에 어울려?",
                    hair + "에 어울리는 코디는?",
                    ctx.secondHair().name() + "도 어울릴까요?");
            case FASHION -> List.of(
                    fashion + " 룩에 어울리는 신발은?",
                    "출근룩으로 입는다면 어떻게 할까요?",
                    ctx.secondFashion().name() + " 룩도 어울릴까요?");
            case ACCESSORY -> List.of(
                    fashion + " 룩에 어울리는 가방은?",
                    hair + "에 어울리는 모자는?",
                    "소개팅 코디를 추천해 줘");
            case GROOMING -> initialPrompts(ctx);                   // 그루밍 질문은 범위 밖이라 칩으로 이어 가지 않음
            case BODY_METRIC -> List.of(
                    ctx.bodyPhrase() + "에 어울리는 바지 핏은?",
                    fashion + " 룩 기본 아이템 조합을 알려 줘",
                    "상의 사이즈는 어떻게 고르면 좋을까요?");
            case GENERAL -> initialPrompts(ctx);
        };
        Set<String> asked = new LinkedHashSet<>();
        askedMessages.forEach(m -> asked.add(m.strip()));
        List<String> result = new ArrayList<>();
        for (String p : candidates) {
            if (!asked.contains(p) && result.size() < MAX_PROMPTS) {
                result.add(p);
            }
        }
        for (String p : initialPrompts(ctx)) {
            if (result.size() >= MIN_PROMPTS) {
                break;
            }
            if (!asked.contains(p) && !result.contains(p)) {
                result.add(p);
            }
        }
        return List.copyOf(result);
    }
}
