package com.moda.chat.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.global.config.ModaProperties;
import com.moda.global.config.ModaProperties.ClientMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code moda.chat.mode}({@code mock} | {@code external})에 따라 {@link ChatClient} 구현체를 선택합니다. */
@Configuration
public class ChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatClientConfig.class);

    @Bean
    public ChatClient chatClient(ModaProperties properties, ObjectMapper objectMapper) {
        ModaProperties.Chat chat = properties.chat();
        if (chat.mode() == ClientMode.EXTERNAL) {
            log.info("[Chat] moda.chat.mode=external — {} (model={})", chat.baseUrl(), chat.model());
            return new ExternalLlmChatClient(chat, objectMapper);
        }
        log.info("[Chat] moda.chat.mode=mock — 템플릿 답변 사용 (기본 지연 {}ms)", chat.mockDefaultDelay().toMillis());
        return new MockChatClient(chat.mockDefaultDelay());
    }
}
