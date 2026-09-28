package com.example.digitalhuman;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通道可切换的回归门禁：业务代码只依赖 ChatClient，
 * 把配置从 DashScope 换成 DeepSeek（OpenAI 兼容）后，装配出来的模型必须真的换掉。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@ActiveProfiles("deepseek")
class DeepSeekChannelTest {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatModel chatModel;

    @Test
    @DisplayName("deepseekProfile_shouldSwitchChatModelToOpenAiCompatible")
    void deepseekProfile_shouldSwitchChatModelToOpenAiCompatible() {
        assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
        assertThat(chatClient).isNotNull();
    }
}
