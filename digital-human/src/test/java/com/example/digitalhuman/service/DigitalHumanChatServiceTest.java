package com.example.digitalhuman.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务层单测：用假的 ChatModel 与假的账本撑起真实 ChatClient，全程不联网。
 */
class DigitalHumanChatServiceTest {

    private static ChatModel chatModelReturning(String content) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(content)))));
        return chatModel;
    }

    private static DigitalHumanChatService serviceWith(ChatModel chatModel, AgentConfigRepository configs) {
        com.example.digitalhuman.tools.ToolRegistry tools =
                mock(com.example.digitalhuman.tools.ToolRegistry.class);
        when(tools.readOnly()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        when(tools.remote()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        when(tools.write()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        return new DigitalHumanChatService(ChatClient.builder(chatModel).build(), configs,
                new DigitalHumanChatProperties("你是一个数字人助手，回答简短、口语化。"),
                new com.example.digitalhuman.ai.ChatOptionsFactory(),
                mock(ChatLedgerService.class), new ConversationGuard(), tools);
    }

    @Test
    @DisplayName("answer_shouldUseConfiguredSystemPrompt_whenProjectMissing")
    void answer_shouldUseConfiguredSystemPrompt_whenProjectMissing() {
        ChatModel chatModel = chatModelReturning("我是一个数字人助手，可以陪你聊天。");
        DigitalHumanChatService service = serviceWith(chatModel, mock(AgentConfigRepository.class));

        String answer = service.answer(new ConversationRequest(null, "s1", null, "用一句话介绍你自己"));

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

        assertThatThrownBy(() -> new ConversationRequest(null, "s1", null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("answer_shouldRecordUserAndAssistantMessagesInLedger")
    void answer_shouldRecordUserAndAssistantMessagesInLedger() {
        ChatModel chatModel = chatModelReturning("深圳很值得逛。");
        ChatLedgerService ledger = mock(ChatLedgerService.class);
        com.example.digitalhuman.tools.ToolRegistry tools =
                mock(com.example.digitalhuman.tools.ToolRegistry.class);
        when(tools.readOnly()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        when(tools.remote()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        when(tools.write()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        DigitalHumanChatService service = new DigitalHumanChatService(
                ChatClient.builder(chatModel).build(), mock(AgentConfigRepository.class),
                new DigitalHumanChatProperties("默认人设"), new com.example.digitalhuman.ai.ChatOptionsFactory(),
                ledger, new ConversationGuard(), tools);

        service.answer(new ConversationRequest(null, "s2", 7L, "深圳有什么好玩的"));

        var roleCaptor = org.mockito.ArgumentCaptor.forClass(com.example.digitalhuman.domain.ChatMessage.Role.class);
        var statusCaptor = org.mockito.ArgumentCaptor.forClass(com.example.digitalhuman.domain.ChatMessage.Status.class);
        verify(ledger, org.mockito.Mockito.times(2)).append(any(), any(), any(), roleCaptor.capture(),
                any(), statusCaptor.capture());
        assertThat(roleCaptor.getAllValues()).containsExactly(
                com.example.digitalhuman.domain.ChatMessage.Role.USER,
                com.example.digitalhuman.domain.ChatMessage.Role.ASSISTANT);
        assertThat(statusCaptor.getAllValues()).containsOnly(
                com.example.digitalhuman.domain.ChatMessage.Status.COMPLETED);
    }
}
