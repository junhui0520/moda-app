package com.moda.seed.domain;

/** 추천 근거 문구 종류 (style-rules.md 2장 4번). */
public enum ReasonType {
    /** 기본 점수 전용 문구 (기본 점수 4점 이상 조합) — FACE_* / BODY_* */
    BASE_SPECIFIC,
    /** 기본 점수 일반 문구 (기본 점수 3점 이하 조합) — HAIR_GENERIC_* / FASHION_GENERIC_* */
    BASE_GENERIC,
    /** 양수 보정 근거 문구 — ADJ_* */
    ADJUSTMENT
}
