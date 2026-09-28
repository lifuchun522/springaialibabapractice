package com.example.digitalhuman.repository;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import com.example.digitalhuman.domain.ChatMessage;

/**
 * 把对话记忆放到库里的实现（第 18 掌，「状态可外置」）。
 *
 * <p>为什么必须换掉内存实现：{@code InMemoryChatMemoryRepository} 是**进程内**的。
 * 多副本之后，请求打到 A 副本、会话在 B 副本，用户看到的是「AI 失忆」——
 * 这不是部署问题，是状态归属问题。K8s 把 Pod 当作一次性资源（随时可被替换、驱逐），
 * 把对话记忆放进 Pod 的堆内存，就是在跟这个设计意图对抗。
 *
 * <p>三个关键设计决定，都写在这里而不是散在代码里：
 * <ol>
 *   <li><b>真源是账本，不是 Memory</b>（第 5 掌定下的边界）。{@code chat_message} 表本来就在
 *       每一轮写入（用户提问、助手回答），所以 {@link #saveAll} 是**有意为空**的：
 *       记忆是对账本的一次投影，再写一遍就是双份记录，早晚不一致。</li>
 *   <li><b>只读「有效」的历史</b>：{@code FAILED} 的记录里是错误摘要、{@code CANCELLED}
 *       是半截回答、{@code PENDING} 还没落定。把它们喂回模型，等于让模型把「上次失败了」
 *       当成对话内容。</li>
 *   <li><b>会话键是 conversationId</b>（projectId + userId + sessionId 三层键），不是 sessionId：
 *       同一个浏览器会话在不同登录用户下是两个会话，按 sessionId 读会把两个人的上下文串在一起。</li>
 * </ol>
 *
 * <p>窗口裁剪不在这里做：{@code MessageWindowChatMemory} 会按 {@code max-messages} 再截一次，
 * 这里只负责「把这个会话的有效历史按时间顺序取出来」。
 */
public class LedgerChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(LedgerChatMemoryRepository.class);

    private final ChatMessageRepository messages;

    public LedgerChatMemoryRepository(ChatMessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public List<String> findConversationIds() {
        return messages.findDistinctConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        List<Message> history = messages.findByConversationIdOrderByIdAsc(conversationId).stream()
                .filter(LedgerChatMemoryRepository::isUsable)
                .map(LedgerChatMemoryRepository::toMessage)
                .filter(java.util.Objects::nonNull)
                .toList();
        log.debug("记忆读取 conversationId={} 条数={}", conversationId, history.size());
        return history;
    }

    /**
     * 有意为空：账本已经在每一轮写入（见 {@code ChatLedgerService}），
     * 这里再写一次就会出现两份真源。**空实现要被解释，否则下一个人会以为漏了。**
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        log.trace("记忆写入被跳过（真源是 chat_message 账本）conversationId={} 条数={}",
                conversationId, messages == null ? 0 : messages.size());
    }

    /**
     * 删除某个会话的记忆：删的是**账本里的那批记录**。
     *
     * <p>这一点与内存实现不同：内存删除是清理缓存，这里删除是不可逆的。所以它不该被
     * 「清一下上下文」这类请求随手调用；真要清理，走运维流程。
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        List<ChatMessage> existing = messages.findByConversationIdOrderByIdAsc(conversationId);
        if (!existing.isEmpty()) {
            messages.deleteAll(existing);
            log.warn("记忆被删除 conversationId={} 条数={}（删的是账本，不可逆）",
                    conversationId, existing.size());
        }
    }

    /** 只把「落定的对话内容」当记忆：失败、取消、进行中的记录都不算。 */
    private static boolean isUsable(ChatMessage message) {
        return message.getStatus() == ChatMessage.Status.COMPLETED
                && message.getContent() != null
                && !message.getContent().isBlank();
    }

    private static Message toMessage(ChatMessage message) {
        return switch (message.getRole()) {
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
            default -> null;
        };
    }
}
