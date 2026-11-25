package com.moda.chat.model;

import java.util.List;
import java.util.Locale;

/**
 * 사용자 질문의 주제 분류 (키워드 기반). Mock 답변과 후속 추천 질문 칩 생성에 사용합니다.
 * 우선순위: 체중 수치 → 액세서리 → 그루밍 → 패션 → 헤어 → 일반.
 */
public enum ChatTopic {
    BODY_METRIC(List.of("몸무게", "체중", "bmi", "비만", "살 빼", "살빼", "다이어트", "체지방", "kg", "킬로")),
    ACCESSORY(List.of("안경", "선글라스", "모자", "볼캡", "비니", "액세서리", "악세사리", "시계", "가방", "목걸이", "반지")),
    GROOMING(List.of("피부", "그루밍", "면도", "수염", "향수", "눈썹", "스킨", "로션", "선크림", "세안")),
    FASHION(List.of("코디", "옷", "룩", "패션", "아이템", "셔츠", "바지", "팬츠", "재킷", "자켓", "코트", "니트", "신발",
            "스니커즈", "구두", "출근", "소개팅", "데이트", "면접", "하객", "결혼식", "여행", "입으", "입을", "입는")),
    HAIR(List.of("헤어", "머리", "펌", "가르마", "앞머리", "커트", "컷", "왁스", "스타일링", "드라이", "염색", "이발",
            "미용실", "포마드", "깐머리", "쉐도우", "크롭")),
    GENERAL(List.of());

    private final List<String> keywords;

    ChatTopic(List<String> keywords) {
        this.keywords = keywords;
    }

    public static ChatTopic detect(String message) {
        if (message == null) {
            return GENERAL;
        }
        String m = message.toLowerCase(Locale.ROOT);
        for (ChatTopic topic : values()) {
            if (topic.keywords.stream().anyMatch(m::contains)) {
                return topic;
            }
        }
        return GENERAL;
    }

    /** 패션 질문의 상황(TPO). 없으면 null. */
    public static String occasion(String message) {
        if (message == null) {
            return null;
        }
        if (message.contains("소개팅") || message.contains("데이트")) {
            return "소개팅이나 데이트";
        }
        if (message.contains("출근") || message.contains("면접") || message.contains("회사")) {
            return "출근이나 면접";
        }
        if (message.contains("하객") || message.contains("결혼식")) {
            return "결혼식 하객";
        }
        if (message.contains("여행")) {
            return "여행";
        }
        return null;
    }
}
