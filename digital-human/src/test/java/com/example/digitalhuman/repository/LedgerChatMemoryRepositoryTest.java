package com.example.digitalhuman.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;

/**
 * 第 18 掌：**状态可外置**的第一条——对话记忆不能活在 Pod 堆里。
 *
 * <p>这条用例证明的是多副本场景的核心性质：两个「实例」（这里用两个仓储对象模拟）
 * 读同一个会话，拿到的是同一段历史。内存实现做不到这件事，
 * 表现就是文章 01 章那句「请求打到 A 副本，会话在 B 副本，用户看到 AI 失忆」。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=memory-test-key-not-a-secret",
        // 这条用例专门测账本实现，所以显式选它（测试 profile 默认是 in-memory）
        "digital-human.memory.store=jdbc"
})
@ActiveProfiles("test")
class LedgerChatMemoryRepositoryTest {

    @Autowired
    private LedgerChatMemoryRepository memory;

    @Autowired
    private ChatLedgerService ledger;

    @Autowired
    private ChatMessageRepository messages;

    private String conversationKey(String tag) {
        return ConversationId.of(7L, 1L, "s-" + tag).value();
    }

    @Test
    @DisplayName("第二个实例能读到第一个实例写入的会话历史（多副本不失忆）")
    void anotherInstanceSeesTheSameHistory() {
        String conversationId = conversationKey("multi-replica");

        // 实例 A：走真实账本写入路径（业务代码就是这么写的）
        ledger.append(1L, ConversationId.of(7L, 1L, "s-multi-replica"), 7L,
                ChatMessage.Role.USER, "深圳展厅周六还有位吗？", ChatMessage.Status.COMPLETED);
        ledger.append(1L, ConversationId.of(7L, 1L, "s-multi-replica"), 7L,
                ChatMessage.Role.ASSISTANT, "周六下午还有 3 个名额。", ChatMessage.Status.COMPLETED);

        // 实例 B：另一个仓储对象，只靠数据库
        LedgerChatMemoryRepository instanceB = new LedgerChatMemoryRepository(messages);
        List<Message> history = instanceB.findByConversationId(conversationId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0)).isInstanceOf(UserMessage.class);
        assertThat(history.get(0).getText()).isEqualTo("深圳展厅周六还有位吗？");
        assertThat(history.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(history.get(1).getText()).isEqualTo("周六下午还有 3 个名额。");
    }

    @Test
    @DisplayName("失败与取消的记录不进记忆：否则模型会把「上次失败了」当成对话内容")
    void onlySettledTurnsBecomeMemory() {
        ConversationId conversationId = ConversationId.of(7L, 1L, "s-status");
        ledger.append(1L, conversationId, 7L, ChatMessage.Role.USER, "问题一", ChatMessage.Status.COMPLETED);
        ledger.append(1L, conversationId, 7L, ChatMessage.Role.ASSISTANT,
                "[PROVIDER_ERROR] 模型提供方拒绝了这次调用", ChatMessage.Status.FAILED);
        ledger.append(1L, conversationId, 7L, ChatMessage.Role.ASSISTANT, "半截回答", ChatMessage.Status.CANCELLED);
        ledger.append(1L, conversationId, 7L, ChatMessage.Role.ASSISTANT, "正常回答", ChatMessage.Status.COMPLETED);

        List<Message> history = memory.findByConversationId(conversationId.value());

        assertThat(history).extracting(Message::getText)
                .containsExactly("问题一", "正常回答");
    }

    @Test
    @DisplayName("会话隔离：按 conversationId 读，不会拿到别人的会话")
    void conversationsAreIsolated() {
        ledger.append(1L, ConversationId.of(7L, 1L, "s-owner-a"), 7L,
                ChatMessage.Role.USER, "A 的问题", ChatMessage.Status.COMPLETED);
        ledger.append(1L, ConversationId.of(8L, 1L, "s-owner-a"), 8L,
                ChatMessage.Role.USER, "B 的问题", ChatMessage.Status.COMPLETED);

        assertThat(memory.findByConversationId(ConversationId.of(7L, 1L, "s-owner-a").value()))
                .extracting(Message::getText).containsExactly("A 的问题");
        assertThat(memory.findByConversationId(ConversationId.of(8L, 1L, "s-owner-a").value()))
                .extracting(Message::getText).containsExactly("B 的问题");
    }

    @Test
    @DisplayName("写是有意空实现：账本已经是真源，再写一遍就是双份记录")
    void saveIsIntentionallyANoOp() {
        String conversationId = conversationKey("noop");
        long before = messages.count();

        memory.saveAll(conversationId, List.of(new UserMessage("不该被写进去")));

        assertThat(messages.count()).as("记忆写入不应产生新的账本记录").isEqualTo(before);
        assertThat(memory.findByConversationId(conversationId)).isEmpty();
    }

    @Test
    @DisplayName("空会话键不报错：宁可返回空历史，也不要在请求路径上抛异常")
    void blankConversationIdIsSafe() {
        assertThat(memory.findByConversationId(null)).isEmpty();
        assertThat(memory.findByConversationId("  ")).isEmpty();
    }
}
