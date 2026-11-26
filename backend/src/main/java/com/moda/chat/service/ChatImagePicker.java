package com.moda.chat.service;

import com.moda.chat.model.ChatContext;
import com.moda.chat.model.ChatTopic;
import com.moda.seed.domain.FashionStyle;
import com.moda.seed.domain.HairStyle;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 챗봇 답변에 붙일 스타일 이미지 키(catalog {@code imageKey}) 선택.
 * <ul>
 *   <li>질문 주제가 헤어면 헤어 카탈로그, 패션이면 패션 카탈로그에서 <b>답변에 이름이 나온 스타일</b>을 언급 순서대로 최대 2개</li>
 *   <li>언급된 스타일이 없으면 해당 분야 1순위 추천 스타일 1개, 일반 질문이면 1순위 헤어·패션 각 1개</li>
 *   <li>액세서리·그루밍·체중 질문은 이미지 없음</li>
 * </ul>
 * LLM에 태그 출력을 맡기지 않아 Mock·External 모두 같은 규칙으로 동작합니다. 실제 이미지(URL)는 앱이 {@code imageKey}로 찾습니다.
 */
@Component
public class ChatImagePicker {

    static final int MAX_IMAGES = 2;
    private static final String HAIR_SUFFIX = " 스타일";

    private final HairStyleRepository hairStyles;
    private final FashionStyleRepository fashionStyles;

    public ChatImagePicker(HairStyleRepository hairStyles, FashionStyleRepository fashionStyles) {
        this.hairStyles = hairStyles;
        this.fashionStyles = fashionStyles;
    }

    public List<String> pick(String userMessage, String reply, ChatContext ctx) {
        return switch (ChatTopic.detect(userMessage)) {
            case HAIR -> mentioned(reply, hairKeywords(), ctx.topHair().imageKey());
            case FASHION -> mentioned(reply, fashionKeywords(), ctx.topFashion().imageKey());
            case GENERAL -> {                                                  // 답변이 스타일을 언급했을 때만 (기준·설명 질문엔 이미지 없음)
                Map<String, String> all = hairKeywords();
                all.putAll(fashionKeywords());
                yield mentioned(reply, all, null);
            }
            case ACCESSORY, GROOMING, BODY_METRIC -> List.of();
        };
    }

    /** imageKey → 스타일 이름 (응답의 {@code images[].label}). */
    public Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        hairStyles.findAll().forEach(h -> labels.put(h.getImageKey(), h.getName()));
        fashionStyles.findAll().forEach(f -> labels.put(f.getImageKey(), f.getName()));
        return labels;
    }

    static List<String> mentioned(String reply, Map<String, String> keywordToKey, String fallback) {
        List<String> keys = keywordToKey.entrySet().stream()
                .filter(e -> reply.contains(e.getKey()))
                .sorted(Comparator.comparingInt(e -> reply.indexOf(e.getKey())))
                .map(Map.Entry::getValue)
                .distinct()
                .limit(MAX_IMAGES)
                .toList();
        return keys.isEmpty() && fallback != null ? List.of(fallback) : keys;
    }

    /** "가르마 스타일" → "가르마" (답변에서 "가르마 펌"처럼 줄여 말해도 잡히도록). */
    private Map<String, String> hairKeywords() {
        Map<String, String> map = new LinkedHashMap<>();
        for (HairStyle h : hairStyles.findAllByOrderBySortOrderAsc()) {
            String name = h.getName();
            map.put(name.endsWith(HAIR_SUFFIX) ? name.substring(0, name.length() - HAIR_SUFFIX.length()) : name, h.getImageKey());
        }
        return map;
    }

    private Map<String, String> fashionKeywords() {
        Map<String, String> map = new LinkedHashMap<>();
        for (FashionStyle f : fashionStyles.findAllByOrderBySortOrderAsc()) {
            map.put(f.getName(), f.getImageKey());
        }
        return map;
    }
}
