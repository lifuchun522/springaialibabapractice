package com.example.digitalhuman;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配级验证：容器里有且只有一条模型通道，且业务代码只看到 ChatClient。
 * 用假 Key 只为让自动装配过关，本测试不发起任何真实模型调用。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@ActiveProfiles("test")
class DigitalHumanApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ChatClient chatClient;

    @Test
    @DisplayName("context_shouldExposeSingleChatClientBean")
    void context_shouldExposeSingleChatClientBean() {
        assertThat(chatClient).isNotNull();
    }

    @Test
    @DisplayName("context_shouldProvideExactlyOneChatModelChannel")
    void context_shouldProvideExactlyOneChatModelChannel() {
        Map<String, ChatModel> chatModels = applicationContext.getBeansOfType(ChatModel.class);

        assertThat(chatModels).hasSize(1);
        assertThat(chatModels.values().iterator().next()).isInstanceOf(OpenAiChatModel.class);
    }
}
