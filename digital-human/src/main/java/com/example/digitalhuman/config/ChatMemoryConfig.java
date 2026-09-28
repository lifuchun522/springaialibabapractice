package com.example.digitalhuman.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短期记忆（模型上下文窗口）的装配点。
 *
 * <p>边界写在代码里：**这是投影，不是账本**。窗口会被裁剪、内存实现重启即丢、
 * 多实例部署时各记各的——产品历史另有 chat_message 账本。
 *
 * <p>跨实例部署时，把 {@link ChatMemoryRepository} 换成 JDBC/Redis 实现即可，
 * 业务代码与 Advisor 参数都不需要改。
 */
@Configuration
@EnableConfigurationProperties(ChatMemoryConfig.MemoryProperties.class)
public class ChatMemoryConfig {

    @ConfigurationProperties(prefix = "digital-human.memory")
    public record MemoryProperties(int maxMessages) {

        public MemoryProperties {
            if (maxMessages <= 0) {
                maxMessages = 20;
            }
        }
    }

    @Bean
    ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, MemoryProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.maxMessages())
                .build();
    }
}
