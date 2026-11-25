package com.moda.chat.client;

import static com.moda.common.text.Josa.eulReul;
import static com.moda.common.text.Josa.euroRo;
import static com.moda.common.text.Josa.iGa;

import com.moda.chat.model.ChatContext;
import com.moda.chat.model.ChatTopic;
import com.moda.chat.service.ChatReplySanitizer;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairThickness;
import com.moda.global.error.BusinessException;
import com.moda.recommendation.dto.RecommendationDtos.FashionRecommendation;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@code moda.chat.mode=mock} 챗봇 클라이언트. LLM 키 없이 세션의 맞춤 유형·얼굴형·체형·추천 스타일과 질문 키워드(주제·상황)를 반영한
 * 3~5문장 한국어 답변을 만듭니다. 표현 가이드(R-11)를 지키며 체중·BMI 수치는 답하지 않습니다.
 * <p>{@code X-Mock-Delay-Ms}(0~10000) 지원, 기본 지연 {@code moda.chat.mock-default-delay}(800ms, 테스트 0ms).
 */
public class MockChatClient implements ChatClient {

    static final String MODEL = "mock-chat-0.1";
    static final int MAX_DELAY_MS = 10_000;
    static final String H_DELAY_MS = "X-Mock-Delay-Ms";

    private static final Map<FaceShape, String> EYEWEAR = Map.of(
            FaceShape.DIAMOND, "광대 라인이 또렷한 다이아몬드형에는 윗선이 살짝 강조된 오벌·브로우 라인 안경테가 잘 어울려요.",
            FaceShape.HEART, "하트형 얼굴에는 아랫부분이 둥근 라운드나 웰링턴 안경테가 시원한 이마 라인과 균형을 맞춰 줘요.",
            FaceShape.OVAL, "계란형은 대부분의 안경테를 잘 소화해서 스퀘어나 보스턴 테로 분위기를 바꿔 보기 좋아요.",
            FaceShape.RECTANGLE, "직사각형 얼굴에는 세로 폭이 넉넉한 라운드·보스턴 테가 또렷한 턱선을 부드럽게 이어 줘요.",
            FaceShape.ROUND, "둥근형 얼굴에는 직선이 살아 있는 스퀘어·웰링턴 테가 산뜻한 인상을 더해 줘요.",
            FaceShape.LONG, "긴형 얼굴에는 가로 폭이 넉넉하고 테가 도톰한 디자인이 세로 비율을 안정적으로 정돈해 줘요.");

    private static final Map<HairCurl, String> CURL_TIP = Map.of(
            HairCurl.STRAIGHT, "직모라면 드라이할 때 뿌리 쪽을 반대 방향으로 말려 주면 볼륨이 오래 유지돼요.",
            HairCurl.SEMI_CURLY, "반곱슬 모발은 결을 살려 가볍게 드라이하면 자연스러운 볼륨이 살아나요.",
            HairCurl.CURLY, "곱슬 모발은 컬을 살리는 크림 타입 제품으로 질감을 정리해 주면 멋스럽게 연출돼요.");

    private static final Map<HairThickness, String> THICKNESS_TIP = Map.of(
            HairThickness.THIN, "가는 모발에는 무거운 왁스보다 가벼운 텍스처 제품을 소량 쓰는 게 좋아요.",
            HairThickness.MEDIUM, "스타일링 제품은 손끝에 소량만 덜어 앞머리부터 정리해 보세요.",
            HairThickness.THICK, "굵은 모발은 힘이 좋아서 하드 타입 제품으로 형태를 잡아 주면 오래 유지돼요.");

    private final Duration defaultDelay;

    public MockChatClient(Duration defaultDelay) {
        this.defaultDelay = defaultDelay;
    }

    @Override
    public ChatReply complete(ChatCompletionCommand command) {
        delay(command.options() == null ? null : command.options().mockDelayMs());
        ChatContext ctx = command.context();
        String message = command.userMessage();
        String reply = switch (ChatTopic.detect(message)) {
            case BODY_METRIC -> ChatReplySanitizer.bodyMetricFallback(ctx);
            case ACCESSORY -> accessory(ctx);
            case GROOMING -> grooming(ctx);
            case FASHION -> fashion(ctx, ChatTopic.occasion(message));
            case HAIR -> hair(ctx);
            case GENERAL -> general(ctx);
        };
        return new ChatReply(reply, MODEL);
    }

    private static String hair(ChatContext ctx) {
        String top = ctx.topHair().name();
        return ctx.faceShape().label() + " 얼굴에는 " + iGa(top) + " 가장 잘 어울려요. "
                + ctx.topHair().reasons().get(0) + " "
                + CURL_TIP.get(ctx.hairCurl()) + " "
                + THICKNESS_TIP.get(ctx.hairThickness()) + " "
                + "두 번째 추천인 " + euroRo(ctx.secondHair().name()) + " 분위기를 바꿔 보는 것도 좋아요.";
    }

    private static String fashion(ChatContext ctx, String occasion) {
        FashionRecommendation top = ctx.topFashion();
        List<String> items = top.itemKeywords();
        String opening = "'" + ctx.typeName() + "' 유형에게는 " + top.name() + " 스타일을 먼저 추천드려요. ";
        String reason = top.reasons().get(0) + " ";
        String combo = occasion == null
                ? "기본 아이템으로 " + items.get(0) + ", " + items.get(1) + ", " + items.get(2) + " 조합부터 시작해 보세요. "
                : occasion + "라면 " + items.get(0) + "에 " + eulReul(items.get(3)) + " 매치해 깔끔하고 단정한 인상을 주는 게 좋아요. ";
        return opening + reason + combo
                + ctx.secondFashion().name() + " 무드의 아이템을 한두 개 섞으면 더 다양하게 연출할 수 있어요.";
    }

    private static String accessory(ChatContext ctx) {
        return EYEWEAR.get(FaceShape.fromKey(ctx.faceShape().key())) + " "
                + "모자는 " + eulReul(ctx.topHair().name()) + " 유지하기 쉬운 볼캡이나 비니가 무난해요. "
                + ctx.topFashion().name() + " 룩과 함께 쓴다면 소재와 컬러 톤을 옷과 비슷하게 맞추는 걸 추천드려요.";
    }

    private static String grooming(ChatContext ctx) {
        return "깔끔한 그루밍은 '" + ctx.typeName() + "' 유형의 장점을 더 또렷하게 살려 줘요. "
                + "아침에는 저자극 클렌저로 세안한 뒤 수분 크림과 선크림까지 챙기면 충분해요. "
                + "눈썹 주변 잔털만 정리해도 " + iGa(ctx.topHair().name()) + " 훨씬 단정해 보여요. "
                + "향수는 " + ctx.topFashion().name() + " 무드에 맞춰 가벼운 시트러스나 우디 계열을 추천드려요.";
    }

    private static String general(ChatContext ctx) {
        return "분석 결과 '" + ctx.typeName() + "' 유형으로 나왔어요. " + ctx.typeDescription() + " "
                + "헤어는 " + ctx.topHair().name() + ", 패션은 " + ctx.topFashion().name() + " 스타일부터 시작해 보는 걸 추천드려요. "
                + "헤어 연출법이나 상황별 코디처럼 궁금한 걸 구체적으로 물어보시면 더 자세히 알려 드릴게요.";
    }

    private void delay(String raw) {
        long ms = defaultDelay.toMillis();
        if (raw != null && !raw.isBlank()) {
            try {
                ms = Long.parseLong(raw.strip());
            } catch (NumberFormatException e) {
                ms = -1;
            }
            if (ms < 0 || ms > MAX_DELAY_MS) {
                throw BusinessException.invalidField(H_DELAY_MS, "0 이상 " + MAX_DELAY_MS + " 이하의 정수여야 합니다.", raw);
            }
        }
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
