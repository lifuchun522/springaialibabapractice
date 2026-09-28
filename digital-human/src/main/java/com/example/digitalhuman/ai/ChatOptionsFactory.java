package com.example.digitalhuman.ai;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.domain.AgentConfig;

/**
 * 适配层：整个工程里**唯一**允许出现 provider 专有类型的地方。
 *
 * <p>业务包不 import 任何一家 SDK 的 Options 类型，换模型才不会变成改代码。
 *
 * <p>参数键按 provider 核验过（2026-09-28 实测）：
 * DeepSeek 的 OpenAI 兼容接口只认 {@code max_tokens}——
 * 传 {@code max_completion_tokens: 16} 时请求虽然 200，但上限被忽略（completion_tokens 实际 94）；
 * 传 {@code max_tokens: 16} 时 finish_reason=length、completion_tokens=16，上限真正生效。
 * 所以这里落到 {@code maxTokens}，并把这个结论记在文档里，而不是照抄别家模型的参数名。
 */
@Component
public class ChatOptionsFactory {

    public static final String PROVIDER_DEEPSEEK = "deepseek";

    public ChatOptions optionsFor(AgentConfig config) {
        String provider = config.getProvider();
        if (PROVIDER_DEEPSEEK.equals(provider)) {
            return OpenAiChatOptions.builder()
                    .model(config.getModel())
                    .temperature(config.getTemperature().doubleValue())
                    .maxTokens(config.getMaxTokens())
                    .build();
        }
        throw new ModelRoutingException("暂不支持的 provider：" + provider
                + "（当前仅在 ChatOptionsFactory 中实现了 deepseek 通道）");
    }
}
