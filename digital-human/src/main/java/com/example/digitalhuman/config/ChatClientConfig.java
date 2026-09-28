package com.example.digitalhuman.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型调用出口的唯一装配点。
 *
 * <p>业务代码只依赖 {@link ChatClient}，不直接注入 ChatModel 等底层对象——
 * 所以「加记忆」「加观测」「换模型」都只改这一处，业务类不动。
 *
 * <p>记忆通过 Advisor 织入调用链：调用方只负责给一个 conversationId，
 * 读窗口与写窗口都发生在 Advisor 里，业务代码看不到消息列表。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, DigitalHumanChatProperties properties, ChatMemory memory) {
        return builder
                .defaultSystem(properties.defaultSystem())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }
}
