package com.example.digitalhuman.web;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.example.digitalhuman.config.DigitalHumanChatProperties;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.service.DigitalHumanChatService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务层单测：用假的 ChatModel 撑起真实 ChatClient，全程不联网。
 */
class DigitalHumanChatServiceTest {

    private static ChatModel chatModelReturning(String content) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(content)))));
        return chatModel;
    }

    private static DigitalHumanChatService serviceWith(ChatModel chatModel, AgentConfigRepository configs) {
        return new DigitalHumanChatService(ChatClient.builder(chatModel).build(), configs,
                new DigitalHumanChatProperties("你是一个数字人助手，回答简短、口语化。"),
                new com.example.digitalhuman.ai.ChatOptionsFactory());
    }

    @Test
    @DisplayName("answer_shouldUseConfiguredSystemPrompt_whenProjectMissing")
    void answer_shouldUseConfiguredSystemPrompt_whenProjectMissing() {
        ChatModel chatModel = chatModelReturning("我是一个数字人助手，可以陪你聊天。");
        DigitalHumanChatService service = serviceWith(chatModel, mock(AgentConfigRepository.class));

        String answer = service.answer(null, "用一句话介绍你自己");

        assertThat(answer).isEqualTo("我是一个数字人助手，可以陪你聊天。");
        var promptCaptor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .anySatisfy(message -> assertThat(message.getText()).contains("你是一个数字人助手"));
    }

    @Test
    @DisplayName("answer_shouldRejectBlankQuestion")
    void answer_shouldRejectBlankQuestion() {
        DigitalHumanChatService service = serviceWith(chatModelReturning("不会被调用"),
                mock(AgentConfigRepository.class));

        assertThatThrownBy(() -> service.answer(null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("参数 q 不能为空");
    }
}
