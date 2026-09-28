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
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatController 单测：用假的 ChatModel 撑起真实 ChatClient，全程不联网。
 */
class ChatControllerTest {

    private static ChatModel chatModelReturning(String content) {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return chatModel;
    }

    @Test
    @DisplayName("chat_shouldReturnModelContent_whenQuestionProvided")
    void chat_shouldReturnModelContent_whenQuestionProvided() {
        ChatModel chatModel = chatModelReturning("我是一个数字人助手，可以陪你聊天。");
        ChatController controller = new ChatController(ChatClient.builder(chatModel).build());

        String answer = controller.chat("用一句话介绍你自己");

        assertThat(answer).isEqualTo("我是一个数字人助手，可以陪你聊天。");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("chat_shouldBindDefaultSystemPrompt_whenBuiltFromProperties")
    void chat_shouldBindDefaultSystemPrompt_whenBuiltFromProperties() {
        ChatModel chatModel = chatModelReturning("你好");
        var properties = new com.example.digitalhuman.config.DigitalHumanChatProperties("你是项目 A 的客服。");
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(properties.defaultSystem())
                .build();
        ChatController controller = new ChatController(chatClient);

        controller.chat("你是谁");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .anySatisfy(message -> assertThat(message.getText()).contains("你是项目 A 的客服。"));
    }

    @Test
    @DisplayName("chat_shouldRejectBlankQuestion")
    void chat_shouldRejectBlankQuestion() {
        ChatController controller = new ChatController(
                ChatClient.builder(chatModelReturning("不会被调用")).build());

        assertThatThrownBy(() -> controller.chat("   "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("参数 q 不能为空");
    }

    @Test
    @DisplayName("properties_shouldFallbackToBuiltinSystemPrompt_whenBlank")
    void properties_shouldFallbackToBuiltinSystemPrompt_whenBlank() {
        var properties = new com.example.digitalhuman.config.DigitalHumanChatProperties("  ");

        assertThat(properties.defaultSystem()).isEqualTo("你是一个数字人助手，回答简短、口语化。");
    }
}
