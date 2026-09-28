package com.example.digitalhuman.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.repository.ChatMessageRepository;

import reactor.core.publisher.Flux;

/**
 * 第 16 掌：**同一个语义在两条契约上必须表现一致**。
 *
 * <p>这条用例来自一次真实验收，不是设计出来的：本掌做 SSE 心跳时用长回答压测，
 * 撞上一次「流式响应一个片段都没有」——服务端返回 200、连接正常关闭、前端什么都没收到，
 * 而账本里这条助手消息被记成了 {@code COMPLETED}（内容长度 0）。
 * 同一次对话换成阻塞式接口，同样的问题会被明确判成 {@code EMPTY_RESPONSE} 失败。
 *
 * <p>所以这里钉住三件事：
 * <ol>
 *   <li>空流必须**报错**，不能当成「正常答完」；</li>
 *   <li>账本必须记 {@code FAILED} 并写明原因，而不是记一条空的 COMPLETED；</li>
 *   <li>失败之后会话锁必须释放（否则这个会话将永远「忙」）。</li>
 * </ol>
 */
@SpringBootTest(properties = {
        // ChatModel 被 mock 掉了，但 provider 的 Api Bean 仍会被自动配置创建：给一个占位密钥，
        // 保证这条用例测的是「空流语义」，而不是「有没有密钥」
        "spring.ai.openai.api-key=empty-stream-test-key-not-a-secret"
})
@ActiveProfiles("test")
class EmptyStreamContractTest {

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private DigitalHumanChatService chatService;

    @Autowired
    private ChatMessageRepository messages;

    @Autowired
    private ConversationGuard guard;

    @Test
    @DisplayName("流式响应为空必须显式失败，并落一条 FAILED 账本")
    void emptyStreamMustFailLoudly() {
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.<ChatResponse>empty());

        ConversationRequest request = new ConversationRequest(null, "ch16-empty", 1L, "请回答我");

        assertThatThrownBy(() -> chatService.stream(request).blockLast())
                .isInstanceOf(ModelInvocationException.class)
                .hasMessageContaining("空内容")
                .satisfies(ex -> assertThat(((ModelInvocationException) ex).kind())
                        .isEqualTo(ModelInvocationException.Kind.EMPTY_RESPONSE));

        List<ChatMessage> ledger = messages.findByProjectIdAndSessionIdOrderByIdAsc(0L, "ch16-empty");
        assertThat(ledger).extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
        assertThat(ledger.get(1).getStatus())
                .as("空回答必须记 FAILED：记成 COMPLETED 会让「前端没显示」变成无法追查的问题")
                .isEqualTo(ChatMessage.Status.FAILED);
        assertThat(ledger.get(1).getContent()).contains("EMPTY_RESPONSE");

        assertThat(guard.tryAcquire(ConversationId.of(1L, null, "ch16-empty").value()))
                .as("失败之后会话锁必须释放，否则这个会话永远「忙」")
                .isTrue();
    }
}
