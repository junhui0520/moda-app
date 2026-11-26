package com.moda.chat.service;

import static com.moda.common.text.Josa.iGa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.chat.model.ChatContext;
import com.moda.chat.service.StyleFactsService.StyleFacts;
import com.moda.global.config.ModaProperties;
import com.moda.seed.repository.BodyTypeDefRepository;
import com.moda.seed.repository.FaceShapeDefRepository;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 챗봇 지식 자료({@code seed/chat-knowledge.json})에서 질문에 맞는 항목을 최대 {@value #MAX_ENTRIES}개 고릅니다.
 * <ul>
 *   <li>후보: 항목 {@code keywords} 중 하나 이상이 질문에 있음</li>
 *   <li>{@code hairStyle}/{@code fashionStyle} 조건: 질문에 그 스타일 이름이 있어야 함</li>
 *   <li>{@code faceShape}/{@code bodyType}/{@code hairCurl}/{@code hairThickness}/{@code hairDensity} 조건: 질문이 값을 직접 말하면
 *       (예: "곱슬", "둥근형") 그 값, 아니면 사용자 분석 결과로 판정</li>
 *   <li>{@code occasion} 조건: 키워드(출근·면접·소개팅 등)로 판정</li>
 *   <li>점수 = 키워드 적중 수 + 질문이 직접 말한 조건마다 2 → 높은 순 (같으면 파일 순서)</li>
 * </ul>
 * 항목이 맞으면 LLM을 거치지 않고 {@link #answer} 문장으로 바로 답합니다 (작은 모델이 자료에 없는 사실을 덧붙이는 문제 방지).
 * shortcut: 키워드 부분 문자열 매칭. 항목이 수백 개로 늘거나 오매칭이 잦으면 임베딩 검색으로 교체.
 */
@Component
public class ChatKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(ChatKnowledgeService.class);

    static final String FILE = "chat-knowledge.json";
    static final int MAX_ENTRIES = 2;
    static final Set<String> CONDITION_KEYS =
            Set.of("hairStyle", "fashionStyle", "faceShape", "bodyType", "hairCurl", "hairThickness", "hairDensity", "occasion");
    private static final Set<String> STYLE_KEYS = Set.of("hairStyle", "fashionStyle");
    private static final Set<String> HAIR_TOPICS = Set.of("hair_style", "hair_care", "accessory");
    private static final String HAIR_SUFFIX = " 스타일";
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](\\s|$)");

    public record KnowledgeEntry(String id, String topic, Map<String, List<String>> appliesTo, List<String> keywords, String text,
                                 String imageKey, boolean reviewed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KnowledgeFile(String version, List<KnowledgeEntry> entries) {
    }

    private final List<KnowledgeEntry> entries;
    private final HairStyleRepository hairStyles;
    private final FashionStyleRepository fashionStyles;
    private final FaceShapeDefRepository faceDefs;
    private final BodyTypeDefRepository bodyDefs;

    public ChatKnowledgeService(ObjectMapper objectMapper, ResourceLoader resourceLoader, ModaProperties properties,
                                HairStyleRepository hairStyles, FashionStyleRepository fashionStyles,
                                FaceShapeDefRepository faceDefs, BodyTypeDefRepository bodyDefs) {
        String location = properties.seed().location();
        this.entries = load(objectMapper, resourceLoader.getResource((location.endsWith("/") ? location : location + "/") + FILE));
        this.hairStyles = hairStyles;
        this.fashionStyles = fashionStyles;
        this.faceDefs = faceDefs;
        this.bodyDefs = bodyDefs;
    }

    /** 파일이 없으면 지식 없이 동작(경고), 형식이 틀리면 기동 실패 — 팀이 오타를 바로 알 수 있도록. */
    static List<KnowledgeEntry> load(ObjectMapper objectMapper, Resource resource) {
        if (!resource.exists()) {
            log.warn("[Chat] {} 없음 — 지식 자료 없이 동작합니다.", FILE);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            List<KnowledgeEntry> list = objectMapper.readValue(in, KnowledgeFile.class).entries();
            Set<String> ids = new HashSet<>();
            for (KnowledgeEntry e : list) {
                if (e.id() == null || !ids.add(e.id())) {
                    throw new IllegalStateException(FILE + ": id가 없거나 중복입니다 — " + e.id());
                }
                if (e.text() == null || e.text().isBlank() || e.keywords() == null || e.keywords().isEmpty() || e.appliesTo() == null) {
                    throw new IllegalStateException(FILE + ": text·keywords·appliesTo가 필요합니다 — " + e.id());
                }
                if (!CONDITION_KEYS.containsAll(e.appliesTo().keySet())) {
                    throw new IllegalStateException(FILE + ": 알 수 없는 appliesTo 키 " + e.appliesTo().keySet() + " — " + e.id());
                }
            }
            log.info("[Chat] 지식 자료 {}개 로드 (검수 완료 {}개)", list.size(), list.stream().filter(KnowledgeEntry::reviewed).count());
            return List.copyOf(list);
        } catch (IOException e) {
            throw new IllegalStateException(FILE + " 읽기 실패: " + e.getMessage(), e);
        }
    }

    public List<KnowledgeEntry> find(String message, ChatContext ctx) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> named = namedInQuestion(message);
        Map<String, String> mine = Map.of(
                "faceShape", ctx.faceShape().key(), "bodyType", ctx.bodyType().key(), "hairCurl", ctx.hairCurl().name(),
                "hairThickness", ctx.hairThickness().name(), "hairDensity", ctx.hairDensity().name());
        record Scored(KnowledgeEntry entry, int score, int order) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int s = score(entries.get(i), message, named, mine);
            if (s > 0) {
                scored.add(new Scored(entries.get(i), s, i));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order))
                .limit(MAX_ENTRIES)
                .map(Scored::entry)
                .toList();
    }

    static int score(KnowledgeEntry e, String message, Map<String, Set<String>> named, Map<String, String> mine) {
        int hits = (int) e.keywords().stream().filter(message::contains).count();
        if (hits == 0) {
            return 0;
        }
        int bonus = 0;
        for (Map.Entry<String, List<String>> c : e.appliesTo().entrySet()) {
            if (c.getKey().equals("occasion")) {
                continue;                                                          // 상황은 키워드로 판정
            }
            Set<String> inQuestion = named.getOrDefault(c.getKey(), Set.of());
            if (STYLE_KEYS.contains(c.getKey()) || !inQuestion.isEmpty()) {
                if (Collections.disjoint(inQuestion, c.getValue())) {
                    return 0;
                }
                bonus += 2;
            } else if (!c.getValue().contains(mine.get(c.getKey()))) {
                return 0;
            }
        }
        return hits + bonus;
    }

    /** 질문이 직접 말한 스타일·얼굴형·체형·모질. */
    private Map<String, Set<String>> namedInQuestion(String m) {
        Map<String, Set<String>> named = new HashMap<>();
        hairStyles.findAll().forEach(h -> {
            String n = h.getName();
            if (m.contains(n.endsWith(HAIR_SUFFIX) ? n.substring(0, n.length() - HAIR_SUFFIX.length()) : n)) {
                named.computeIfAbsent("hairStyle", k -> new HashSet<>()).add(h.getCode());
            }
        });
        fashionStyles.findAll().forEach(f -> {
            if (m.contains(f.getName())) {
                named.computeIfAbsent("fashionStyle", k -> new HashSet<>()).add(f.getCode());
            }
        });
        faceDefs.findAll().forEach(d -> {
            if (m.contains(d.getLabel())) {
                named.computeIfAbsent("faceShape", k -> new HashSet<>()).add(d.getFaceKey());
            }
        });
        bodyDefs.findAll().forEach(d -> {
            if (m.contains(d.getLabel())) {
                named.computeIfAbsent("bodyType", k -> new HashSet<>()).add(d.getBodyKey());
            }
        });
        if (m.contains("반곱슬")) {
            named.put("hairCurl", Set.of("SEMI_CURLY"));
        } else if (m.contains("곱슬")) {
            named.put("hairCurl", Set.of("CURLY"));
        } else if (m.contains("직모")) {
            named.put("hairCurl", Set.of("STRAIGHT"));
        }
        if (m.contains("가는 모발") || m.contains("가는 머리") || m.contains("얇은")) {
            named.put("hairThickness", Set.of("THIN"));
        } else if (m.contains("굵은")) {
            named.put("hairThickness", Set.of("THICK"));
        }
        return named;
    }

    // ------------------------------------------------------------------ 사용처

    public static List<String> imageKeys(List<KnowledgeEntry> found) {
        return found.stream().map(KnowledgeEntry::imageKey).filter(Objects::nonNull).distinct().limit(MAX_ENTRIES).toList();
    }

    /** 최종 답변(LLM 미사용, Mock·External 공통): 자료 문장 + (특정 스타일 질문이면 본인 판정, 아니면 본인 추천 스타일) 한 문장, 최대 5문장. */
    public static String answer(List<KnowledgeEntry> found, StyleFacts facts, ChatContext ctx) {
        StringBuilder sb = new StringBuilder(found.get(0).text());
        if (found.size() > 1 && sentences(sb + " " + found.get(1).text()) <= 4) {
            sb.append(' ').append(found.get(1).text());
        }
        String tail = facts != null ? StyleFactsService.verdictSentence(facts)
                : HAIR_TOPICS.contains(found.get(0).topic())
                        ? ctx.faceShape().label() + " 얼굴에는 " + iGa(ctx.topHair().name()) + " 가장 잘 어울리는 추천 헤어예요."
                        : ctx.bodyPhrase() + "에는 " + iGa(ctx.topFashion().name()) + " 가장 잘 어울리는 추천 패션이에요.";
        return sb.append(' ').append(tail).toString();
    }

    static int sentences(String text) {
        return (int) SENTENCE_END.matcher(text).results().count();
    }
}
