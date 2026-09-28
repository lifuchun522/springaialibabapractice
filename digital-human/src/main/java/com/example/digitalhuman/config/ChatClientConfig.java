package com.example.digitalhuman.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型调用出口的唯一装配点。
 *
 * <p>业务代码只依赖 {@link ChatClient}，不直接注入 ChatModel / DashScope 等底层对象。
 * 这样后面加记忆、加 Advisor、加观测、换模型，都只改这一处。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, DigitalHumanChatProperties properties) {
        return builder
                .defaultSystem(properties.defaultSystem())
                .build();
    }
}
