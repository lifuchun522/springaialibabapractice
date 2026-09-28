package com.example.digitalhuman.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.digitalhuman.repository.ChatMessageRepository;
import com.example.digitalhuman.repository.LedgerChatMemoryRepository;

/**
 * 短期记忆（模型上下文窗口）的装配点。
 *
 * <p>边界写在代码里：**这是投影，不是账本**。窗口会被裁剪、内存实现重启即空；
 * 产品历史另有 {@code chat_message} 账本。
 *
 * <p>第 18 掌的改动就在这个类：默认把记忆仓库换成**库里的实现**
 * （{@code digital-human.memory.store=jdbc}），因为内存实现是进程内的——
 * 多副本之后「请求打到 A 副本、会话在 B 副本」会让用户看到 AI 失忆。
 *
 * <p><b>为什么用 if/else 而不是两个 {@code @ConditionalOnProperty} 的 Bean</b>：
 * 第一版就是那么写的，测试里直接炸了——
 * {@code expected single matching bean but found 2: ledgerChatMemoryRepository,inMemoryChatMemoryRepository}。
 * 两个条件方法各自「看起来」互斥，但条件求值依赖属性解析时机与 profile 覆盖；
 * 一旦属性没按预期解析，得到的是**两个都成立**（而不是两个都不成立），
 * 表现为启动失败。改成一个方法显式选择之后，判断只有一处、可读、可测——
 * **「互斥」应该由代码保证，而不是由条件注解的巧合保证。**
 */
@Configuration
@EnableConfigurationProperties(ChatMemoryConfig.MemoryProperties.class)
public class ChatMemoryConfig {

    @ConfigurationProperties(prefix = "digital-human.memory")
    public record MemoryProperties(int maxMessages, String store) {

        public MemoryProperties {
            if (maxMessages <= 0) {
                maxMessages = 20;
            }
            if (store == null || store.isBlank()) {
                store = "jdbc";
            }
        }

        public boolean ledgerBacked() {
            return !"in-memory".equalsIgnoreCase(store);
        }
    }

    /**
     * 记忆仓库：只此一处决定「记忆放哪里」。
     *
     * <p>默认 jdbc（账本）。进程内记忆在单副本时看不出任何问题，
     * 恰好是最容易被默认值坑到的那类配置——所以它必须被显式选择（测试与本地调试）。
     */
    @Bean
    ChatMemoryRepository chatMemoryRepository(ChatMessageRepository messages, MemoryProperties properties) {
        if (properties.ledgerBacked()) {
            return new LedgerChatMemoryRepository(messages);
        }
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
