package com.example.digitalhuman.ai;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.example.digitalhuman.domain.AgentConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 适配层单测：provider 专有类型只出现在这里。 */
class ChatOptionsFactoryTest {

    private final ChatOptionsFactory factory = new ChatOptionsFactory();

    @Test
    @DisplayName("optionsFor_shouldCarryModelTemperatureAndMaxTokens")
    void optionsFor_shouldCarryModelTemperatureAndMaxTokens() {
        AgentConfig config = AgentConfig.forProject(1L);
        config.setProvider("deepseek");
        config.setModel("deepseek-v4-pro");
        config.setTemperature(new BigDecimal("0.30"));
        config.setMaxTokens(256);

        OpenAiChatOptions options = (OpenAiChatOptions) factory.optionsFor(config);

        assertThat(options.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(options.getTemperature()).isEqualTo(0.30d);
        assertThat(options.getMaxTokens()).isEqualTo(256);
        // DeepSeek 的兼容接口只认 max_tokens，这里显式确认我们发的是它
        assertThat(options.getMaxCompletionTokens()).isNull();
    }

    @Test
    @DisplayName("optionsFor_shouldRejectUnknownProvider")
    void optionsFor_shouldRejectUnknownProvider() {
        AgentConfig config = AgentConfig.forProject(1L);
        config.setProvider("some-other-vendor");

        assertThatThrownBy(() -> factory.optionsFor(config))
                .isInstanceOf(ModelRoutingException.class)
                .hasMessageContaining("暂不支持");
    }
}
