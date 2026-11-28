package com.moda.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code moda.*} 커스텀 설정 (docs/api-spec.md 1.4, 1.5, 부록 A.1 / docs/style-rules.md 5.3).
 */
@Validated
@ConfigurationProperties(prefix = "moda")
public record ModaProperties(
        @Valid @NotNull @DefaultValue Seed seed,
        @Valid @NotNull @DefaultValue Ai ai,
        @Valid @NotNull @DefaultValue Chat chat,
        @Valid @NotNull @DefaultValue Session session) {

    /** 시드 적재 모드: 서버 기동 시 코드 기준 upsert 또는 적재 생략. */
    public enum SeedMode { UPSERT, NONE }

    /** AI 분석 / 챗봇 LLM 호출 모드. */
    public enum ClientMode { MOCK, EXTERNAL }

    public record Seed(
            @NotNull @DefaultValue("upsert") SeedMode mode,
            @NotBlank @DefaultValue("classpath:seed/") String location) {
    }

    public record Ai(
            @NotNull @DefaultValue("mock") ClientMode mode,
            @NotBlank @DefaultValue("http://localhost:8000") String baseUrl,
            @NotNull @DefaultValue("3s") Duration connectTimeout,
            @NotNull @DefaultValue("15s") Duration faceReadTimeout,
            @NotNull @DefaultValue("20s") Duration bodyReadTimeout,
            /* Mock 모드에서 X-Mock-Delay-Ms 헤더가 없을 때의 응답 지연 (api-spec.md 1.5 기본값 800ms) */
            @NotNull @DefaultValue("800ms") Duration mockDefaultDelay) {
    }

    /**
     * 챗봇 LLM 설정 (api-spec.md 1.5 챗봇 Mock, ⑩).
     * {@code external}은 OpenAI 호환 Chat Completions API({@code {base-url}/chat/completions})를 호출하므로
     * {@code base-url}·{@code model}·{@code api-key} 환경변수만 바꾸면 OpenAI·Gemini(OpenAI 호환 엔드포인트) 등에 연결됩니다.
     */
    public record Chat(
            @NotNull @DefaultValue("mock") ClientMode mode,
            @Min(1) @Max(100) @DefaultValue("20") int maxTurns,
            @NotNull @DefaultValue("3s") Duration connectTimeout,
            @NotNull @DefaultValue("20s") Duration readTimeout,
            @DefaultValue("") String apiKey,
            @NotBlank @DefaultValue("https://api.openai.com/v1") String baseUrl,
            @NotBlank @DefaultValue("gpt-4o-mini") String model,
            @DefaultValue("0.7") double temperature,
            @Min(16) @Max(4096) @DefaultValue("400") int maxTokens,
            /* Mock 모드에서 X-Mock-Delay-Ms 헤더가 없을 때의 응답 지연 (api-spec.md 1.5 기본값 800ms) */
            @NotNull @DefaultValue("800ms") Duration mockDefaultDelay) {

        /** API 키는 로그·toString에 노출하지 않는다. */
        @Override
        public String toString() {
            return "Chat[mode=" + mode + ", maxTurns=" + maxTurns + ", baseUrl=" + baseUrl + ", model=" + model
                    + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                    + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "(unset)" : "****") + "]";
        }
    }

    public record Session(
            @Min(1) @DefaultValue("30") int ttlMinutes,
            @NotNull @DefaultValue("5m") Duration cleanupInterval) {
    }
}
