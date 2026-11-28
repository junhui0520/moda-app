package com.moda.common.enums;

/**
 * API·DB·AI에서 소문자 Key로 표기되는 Enum(얼굴형·체형) 공통 인터페이스.
 */
public interface KeyedEnum {

    /** API 응답·DB 저장·AI 연동에 쓰는 소문자 Key (예: {@code heart}, {@code small_inv_triangle}). */
    String key();
}
