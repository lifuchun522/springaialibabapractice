package com.example.digitalhuman;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配级验证：ChatClient 唯一出口必须由容器提供。
 * 用假 Key 只为让自动装配过关，本测试不发起任何真实模型调用。
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-key-not-used")
class DigitalHumanApplicationTest {

    @Autowired
    private ChatClient chatClient;

    @Test
    @DisplayName("context_shouldExposeSingleChatClientBean")
    void context_shouldExposeSingleChatClientBean() {
        assertThat(chatClient).isNotNull();
    }
}
