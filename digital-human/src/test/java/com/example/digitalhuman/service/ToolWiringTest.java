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
import org.springframework.ai.tool.ToolCallback;

import com.example.digitalhuman.config.DigitalHumanChatProperties;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.tools.ToolRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「写工具默认不交给模型」这条边界必须落在代码里，而不是文档里。
 *
 * <p>断言方式：看服务到底向注册中心要了哪几套工具——带了写工具就是越界，测出来最直接。
 */
class ToolWiringTest {

    private static ToolCallback[] callbacks(String name) {
        return new ToolCallback[]{new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name(name).description("测试").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }
        }};
    }

    private static DigitalHumanChatService serviceWith(ToolRegistry registry, ChatModel chatModel) {
        return new DigitalHumanChatService(ChatClient.builder(chatModel).build(),
                mock(AgentConfigRepository.class), new DigitalHumanChatProperties("默认人设"),
                new com.example.digitalhuman.ai.ChatOptionsFactory(), mock(ChatLedgerService.class),
                new ConversationGuard(), registry);
    }

    private static ChatModel chatModel() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("好的。")))));
        return chatModel;
    }

    @Test
    @DisplayName("answer_shouldRegisterReadOnlyToolsOnly_byDefault")
    void answer_shouldRegisterReadOnlyToolsOnly_byDefault() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.readOnly()).thenReturn(callbacks("getProjectInfo"));
        when(registry.write()).thenReturn(callbacks("proposeTitleChange"));

        serviceWith(registry, chatModel()).answer(new ConversationRequest(null, "s1", 1L, "你好"));

        verify(registry).readOnly();
        verify(registry, never()).write();
    }

    @Test
    @DisplayName("answer_shouldRegisterWriteTools_whenExplicitlyRequested")
    void answer_shouldRegisterWriteTools_whenExplicitlyRequested() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.readOnly()).thenReturn(callbacks("getProjectInfo"));
        when(registry.write()).thenReturn(callbacks("proposeTitleChange"));

        serviceWith(registry, chatModel()).answer(new ConversationRequest(null, "s1", 1L, "改标题", true));

        verify(registry).readOnly();
        verify(registry).write();
    }

    @Test
    @DisplayName("request_shouldAlwaysCarryTraceIdForAudit")
    void request_shouldAlwaysCarryTraceIdForAudit() {
        ConversationRequest request = new ConversationRequest(null, "s1", 1L, "你好");

        assertThat(request.traceId()).isNotBlank();
        assertThat(request.allowWrite()).isFalse();
        assertThat(new ConversationRequest(null, "s1", 1L, "你好", true).allowWrite()).isTrue();
    }
}
