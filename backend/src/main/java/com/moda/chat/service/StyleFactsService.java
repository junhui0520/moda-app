package com.moda.chat.service;

import com.moda.chat.model.ChatContext;
import static com.moda.common.text.Josa.eunNeun;

import com.moda.chat.model.ChatTopic;
import com.moda.seed.domain.BodyTypeDef;
import com.moda.seed.domain.FaceShapeDef;
import com.moda.seed.domain.FashionStyle;
import com.moda.seed.domain.HairStyle;
import com.moda.seed.domain.ReasonType;
import com.moda.seed.domain.RuleDomain;
import com.moda.seed.domain.RuleReason;
import com.moda.seed.domain.RuleScore;
import com.moda.seed.domain.ScoreType;
import com.moda.seed.repository.BodyTypeDefRepository;
import com.moda.seed.repository.FaceShapeDefRepository;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import com.moda.seed.repository.RuleReasonRepository;
import com.moda.seed.repository.RuleScoreRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * "가르마도 어울릴까?"처럼 <b>특정 스타일</b>을 묻는 질문에 쓸 근거를 style-rules.md 규칙표(DB 시드)에서 뽑습니다.
 * <ol>
 *   <li>그 스타일이 어울리는 사람의 특징 — 기본 점수 4점 이상인 얼굴형(헤어) / 체형(패션)과 전용 근거 문구</li>
 *   <li>사용자는 어떤지 — 사용자 얼굴형·체형의 점수 구간과 근거 문구, 모질 보정(헤어)</li>
 *   <li>대안 — 사용자의 추천 순위 상위 스타일</li>
 * </ol>
 * 이 근거로 LLM 없이 {@link #answer} 문장으로 바로 답합니다. 점수 숫자는 화면에 노출하지 않습니다(R-11).
 */
@Component
public class StyleFactsService {

    private static final String HAIR_SUFFIX = " 스타일";

    private final HairStyleRepository hairStyles;
    private final FashionStyleRepository fashionStyles;
    private final FaceShapeDefRepository faceDefs;
    private final BodyTypeDefRepository bodyDefs;
    private final RuleScoreRepository scores;
    private final RuleReasonRepository reasons;

    public StyleFactsService(HairStyleRepository hairStyles, FashionStyleRepository fashionStyles, FaceShapeDefRepository faceDefs,
                             BodyTypeDefRepository bodyDefs, RuleScoreRepository scores, RuleReasonRepository reasons) {
        this.hairStyles = hairStyles;
        this.fashionStyles = fashionStyles;
        this.faceDefs = faceDefs;
        this.bodyDefs = bodyDefs;
        this.scores = scores;
        this.reasons = reasons;
    }

    /** @param level 3 = 특히/잘 어울림(기본 4~5점), 2 = 무난(3점), 1 = 다른 스타일이 더 유리(2점 이하) */
    public record StyleFacts(String styleName, boolean hair, String myLabel, int level, boolean best, String myReason,
                             List<String> whoSuitsLabels, List<String> whoSuitsReasons, List<String> myTips,
                             List<String> alternatives) {
    }

    private record Candidate(String keyword, String code, String name, RuleDomain domain) {
    }

    /** 질문에 특정 스타일 이름이 있으면 그 스타일의 근거를, 없으면 비어 있음. */
    public Optional<StyleFacts> find(String message, ChatContext ctx) {
        for (Candidate c : candidates(ChatTopic.detect(message) == ChatTopic.FASHION)) {
            if (message.contains(c.keyword())) {
                return Optional.of(build(c, ctx));
            }
        }
        return Optional.empty();
    }

    private List<Candidate> candidates(boolean fashionFirst) {
        List<Candidate> hair = new ArrayList<>();
        for (HairStyle h : hairStyles.findAllByOrderBySortOrderAsc()) {
            String n = h.getName();
            hair.add(new Candidate(n.endsWith(HAIR_SUFFIX) ? n.substring(0, n.length() - HAIR_SUFFIX.length()) : n, h.getCode(), n,
                    RuleDomain.HAIR));
        }
        List<Candidate> fashion = new ArrayList<>();
        for (FashionStyle f : fashionStyles.findAllByOrderBySortOrderAsc()) {
            fashion.add(new Candidate(f.getName(), f.getCode(), f.getName(), RuleDomain.FASHION));
        }
        List<Candidate> all = new ArrayList<>(fashionFirst ? fashion : hair);
        all.addAll(fashionFirst ? hair : fashion);
        return all;
    }

    private StyleFacts build(Candidate c, ChatContext ctx) {
        boolean hair = c.domain() == RuleDomain.HAIR;
        String factor = hair ? "faceShape" : "bodyType";
        String myKey = hair ? ctx.faceShape().key() : ctx.bodyType().key();
        Map<String, String> labels = new LinkedHashMap<>();                        // 얼굴형/체형 key → 라벨 (표 순서)
        if (hair) {
            faceDefs.findAllByOrderByFaceIndexAsc().forEach((FaceShapeDef d) -> labels.put(d.getFaceKey(), d.getLabel()));
        } else {
            bodyDefs.findAllByOrderByBodyIndexAsc().forEach((BodyTypeDef d) -> labels.put(d.getBodyKey(), d.getLabel()));
        }
        List<RuleScore> allScores = scores.findAllByRuleDomain(c.domain());
        List<RuleReason> allReasons = reasons.findAllByRuleDomain(c.domain());

        Map<String, Integer> base = new LinkedHashMap<>();
        allScores.stream()
                .filter(s -> s.getScoreType() == ScoreType.BASE && s.getFactor().equals(factor) && s.getStyleCode().equals(c.code()))
                .forEach(s -> base.put(s.getConditionValue(), s.getScore()));

        List<String> suitLabels = new ArrayList<>();
        List<String> suitReasons = new ArrayList<>();
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (base.getOrDefault(e.getKey(), 0) >= 4) {
                suitLabels.add(e.getValue());
                suitReasons.add(e.getValue() + ": " + specificReason(allReasons, e.getKey(), c.code()).orElse(""));
            }
        }

        int myScore = base.getOrDefault(myKey, 0);
        int level = myScore >= 4 ? 3 : myScore == 3 ? 2 : 1;
        int max = base.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        String myReason = specificReason(allReasons, myKey, c.code()).orElseGet(() -> allReasons.stream()
                .filter(r -> r.getReasonType() == ReasonType.BASE_GENERIC && r.getStyleCode().equals(c.code()))
                .map(RuleReason::getReasonText).findFirst().orElse(""));

        List<String> myTips = new ArrayList<>();                                   // 모질 보정은 헤어만 (패션 보정은 BMI·키·나이라 노출하지 않음)
        if (hair) {
            Map<String, String> mine = Map.of("hairThickness", ctx.hairThickness().name(), "hairCurl", ctx.hairCurl().name(),
                    "hairDensity", ctx.hairDensity().name());
            allReasons.stream()
                    .filter(r -> r.getReasonType() == ReasonType.ADJUSTMENT && r.getStyleCode().equals(c.code())
                            && r.getConditionValue().equals(mine.get(r.getFactor())))
                    .forEach(r -> myTips.add(r.getReasonText()));
        }

        List<String> alternatives = (hair ? ctx.hairStyles().stream().map(h -> h.name())
                : ctx.fashionStyles().stream().map(f -> f.name()))
                .filter(n -> !n.equals(c.name())).limit(2).toList();

        return new StyleFacts(c.name(), hair, labels.getOrDefault(myKey, myKey), level, myScore == max && max >= 4, myReason,
                suitLabels, suitReasons, myTips, alternatives);
    }

    private static Optional<String> specificReason(List<RuleReason> all, String key, String styleCode) {
        return all.stream()
                .filter(r -> r.getReasonType() == ReasonType.BASE_SPECIFIC && key.equals(r.getConditionValue()) && r.getStyleCode().equals(styleCode))
                .map(RuleReason::getReasonText).findFirst();
    }

    /** "본인은 하트형이라 이 스타일이 가장 잘 맞는 조합이에요." */
    public static String verdictSentence(StyleFacts f) {
        return "본인은 " + f.myLabel() + "이라 " + switch (f.level()) {
            case 3 -> f.best() ? "이 스타일이 가장 잘 맞는 조합이에요." : "이 스타일이 잘 어울려요.";
            case 2 -> "이 스타일도 무난하게 소화할 수 있어요.";
            default -> "이 스타일보다 다른 스타일이 더 잘 맞아요.";
        };
    }

    /** 최종 답변 (LLM 미사용, Mock·External 공통): ① 어울리는 사람의 특징 ② 본인 판정·이유 ③ 모질 팁 또는 대안, 3~5문장. */
    public static String answer(StyleFacts f) {
        String kind = f.hair() ? "얼굴형" : "체형";
        StringBuilder sb = new StringBuilder();
        sb.append(eunNeun(f.styleName())).append(' ');
        sb.append(f.whoSuitsLabels().isEmpty() ? "특정 " + kind + "에 치우치지 않고 두루 어울리는 스타일이에요. "
                : String.join("·", f.whoSuitsLabels()) + "처럼 이 스타일의 장점이 잘 살아나는 " + kind + "에 특히 잘 어울려요. ");
        if (!f.whoSuitsReasons().isEmpty()) {
            sb.append(f.whoSuitsReasons().get(0).replaceFirst("^[^:]*: ", "")).append(' ');
        }
        sb.append(verdictSentence(f)).append(' ');
        sb.append(f.myReason()).append(' ');
        // 3~5문장 유지: 잘 맞으면 모질 팁(없으면 대안), 아니면 대안 제안
        if (f.level() == 3 && !f.myTips().isEmpty()) {
            sb.append(f.myTips().get(0));
        } else {
            sb.append(f.level() == 3 ? "함께 " : "대신 ").append(String.join(", ", f.alternatives()))
                    .append(f.level() == 3 ? "도 고려해 보세요." : " 쪽을 추천드려요.");
        }
        return sb.toString().strip();
    }
}
