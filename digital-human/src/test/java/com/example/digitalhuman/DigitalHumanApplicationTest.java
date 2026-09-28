package com.example.digitalhuman;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配级验证：ChatClient 唯一出口必须由容器提供，且不需要真实密钥即可启动。
 */
@SpringBootTest
class DigitalHumanApplicationTest {

    @Autowired
    private ChatClient chatClient;

    @Test
    @DisplayName("context_shouldExposeSingleChatClientBean")
    void context_shouldExposeSingleChatClientBean() {
        assertThat(chatClient).isNotNull();
    }
}
