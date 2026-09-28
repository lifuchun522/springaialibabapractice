package com.example.digitalhuman.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.example.digitalhuman.ai.ChatOptionsFactory;
import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.config.DigitalHumanChatProperties;
import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.repository.AgentConfigRepository;

/**
 * 一次对话的组装点。
 *
 * <p>业务层只认 {@link ChatClient}、{@code ChatOptions} 和字符串：
 * provider 专有类型全部被关在 {@link ChatOptionsFactory} 里，所以换模型是改数据、不是改这里。
 *
 * <p>另一个职责是把上游异常收敛成 {@link ModelInvocationException}——
 * 业务代码不 catch 任何一家 SDK 的异常体系，否则等于把两家的报错都写死。
 */
@Service
public class DigitalHumanChatService {

    private static final String DEFAULT_PROVIDER = "default";

    private final ChatClient chatClient;
    private final AgentConfigRepository agentConfigs;
    private final DigitalHumanChatProperties chatProperties;
    private final ChatOptionsFactory chatOptionsFactory;

    public DigitalHumanChatService(ChatClient chatClient,
                                   AgentConfigRepository agentConfigs,
                                   DigitalHumanChatProperties chatProperties,
                                   ChatOptionsFactory chatOptionsFactory) {
        this.chatClient = chatClient;
        this.agentConfigs = agentConfigs;
        this.chatProperties = chatProperties;
        this.chatOptionsFactory = chatOptionsFactory;
    }

    /**
     * @param projectId 数字人项目 id；为 null 时走第 1 掌的默认人设与默认模型
     */
    public String answer(Long projectId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("参数 q 不能为空");
        }

        AgentConfig config = projectId == null ? null : agentConfigs.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目缺少 Agent 配置：" + projectId));

        ChatClient.ChatClientRequestSpec spec = config == null
                ? chatClient.prompt().system(chatProperties.defaultSystem())
                : chatClient.prompt().system(config.getSystemPrompt())
                        .options(chatOptionsFactory.optionsFor(config));

        String provider = config == null ? DEFAULT_PROVIDER : config.getProvider();
        String model = config == null ? DEFAULT_PROVIDER : config.getModel();
        return invoke(spec, question, provider, model);
    }

    private String invoke(ChatClient.ChatClientRequestSpec spec, String question,
                          String provider, String model) {
        String content;
        try {
            content = spec.user(question.trim()).call().content();
        } catch (NonTransientAiException ex) {
            throw new ModelInvocationException(isAuthFailure(ex) ? ModelInvocationException.Kind.AUTH
                    : ModelInvocationException.Kind.PROVIDER_ERROR,
                    provider, model, "模型提供方拒绝了这次调用：" + ex.getMessage(), ex);
        } catch (TransientAiException ex) {
            throw new ModelInvocationException(ModelInvocationException.Kind.UNAVAILABLE,
                    provider, model, "模型提供方暂时不可用：" + ex.getMessage(), ex);
        } catch (ResourceAccessException ex) {
            throw new ModelInvocationException(ModelInvocationException.Kind.TIMEOUT,
                    provider, model, "调用模型超时或网络不可达：" + ex.getMessage(), ex);
        }

        if (content == null || content.isBlank()) {
            // 200 但内容为空：最容易伪装成「成功」的一种失败，必须显式失败
            throw new ModelInvocationException(ModelInvocationException.Kind.EMPTY_RESPONSE,
                    provider, model, "模型返回了空内容（HTTP 成功但没有有效回复）", null);
        }
        return content;
    }

    private static boolean isAuthFailure(NonTransientAiException ex) {
        String message = String.valueOf(ex.getMessage());
        return message.contains("401") || message.contains("InvalidApiKey")
                || message.contains("invalid_api_key") || message.contains("Incorrect API key");
    }
}
