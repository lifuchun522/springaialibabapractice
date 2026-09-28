package com.example.digitalhuman.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.example.digitalhuman.config.DigitalHumanChatProperties;
import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.repository.AgentConfigRepository;

/**
 * 一次对话的组装点：System Prompt 来自项目配置，业务代码只面向 ChatClient。
 *
 * <p>本掌只把 System Prompt 接进链路；model / temperature / maxTokens 的生效在第 4 掌。
 */
@Service
public class DigitalHumanChatService {

    private final ChatClient chatClient;
    private final AgentConfigRepository agentConfigs;
    private final DigitalHumanChatProperties chatProperties;

    public DigitalHumanChatService(ChatClient chatClient,
                                   AgentConfigRepository agentConfigs,
                                   DigitalHumanChatProperties chatProperties) {
        this.chatClient = chatClient;
        this.agentConfigs = agentConfigs;
        this.chatProperties = chatProperties;
    }

    public String answer(Long projectId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("参数 q 不能为空");
        }
        return chatClient.prompt()
                .system(systemPromptOf(projectId))
                .user(question.trim())
                .call()
                .content();
    }

    private String systemPromptOf(Long projectId) {
        if (projectId == null) {
            return chatProperties.defaultSystem();
        }
        return agentConfigs.findByProjectId(projectId)
                .map(AgentConfig::getSystemPrompt)
                .orElseThrow(() -> new ResourceNotFoundException("项目缺少 Agent 配置：" + projectId));
    }
}
