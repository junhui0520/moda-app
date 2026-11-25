package com.moda.common.text;

/**
 * 한국어 조사 선택 (받침 유무). 템플릿 문장을 자연스럽게 만들 때 사용합니다.
 * 예: {@code Josa.eunNeun("가르마 스타일")} → {@code "가르마 스타일은"}, {@code Josa.iGa("댄디")} → {@code "댄디가"}
 */
public final class Josa {

    private Josa() {
    }

    /** 마지막 글자가 한글이고 받침이 있으면 true. 한글이 아니면 false(받침 없음으로 처리). */
    public static boolean hasBatchim(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return false;
        }
        return (last - 0xAC00) % 28 != 0;
    }

    /** 받침 ㄹ 여부 ("으로/로" 선택용). */
    private static boolean hasRieulBatchim(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 == 8;
    }

    public static String eunNeun(String word) {
        return word + (hasBatchim(word) ? "은" : "는");
    }

    public static String iGa(String word) {
        return word + (hasBatchim(word) ? "이" : "가");
    }

    public static String eulReul(String word) {
        return word + (hasBatchim(word) ? "을" : "를");
    }

    public static String iNa(String word) {
        return word + (hasBatchim(word) ? "이나" : "나");
    }

    public static String euroRo(String word) {
        return word + (hasBatchim(word) && !hasRieulBatchim(word) ? "으로" : "로");
    }
}
