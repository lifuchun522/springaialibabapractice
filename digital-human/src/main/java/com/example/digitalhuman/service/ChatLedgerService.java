package com.example.digitalhuman.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.repository.ChatMessageRepository;

/**
 * 消息账本：开始、完成、取消、失败都要落一笔。
 *
 * <p>为什么不用 ChatMemory 当历史：Memory 会被窗口裁剪、随重启消失、还可能换实现，
 * 而「产品历史上用户问过什么、我们答了什么、哪条答了一半」是业务事实，必须独立可查。
 */
@Service
public class ChatLedgerService {

    private final ChatMessageRepository messages;

    public ChatLedgerService(ChatMessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public ChatMessage append(Long projectId, ConversationId conversationId, Long userId,
                              ChatMessage.Role role, String content, ChatMessage.Status status) {
        String text = content == null ? "" : content;
        return messages.save(new ChatMessage(projectId, conversationId.value(), conversationId.sessionId(),
                userId, role, text, status));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> history(Long projectId, String sessionId) {
        return messages.findByProjectIdAndSessionIdOrderByIdAsc(projectId, ConversationId.normalizeSession(sessionId));
    }

    /** 历史查询的返回形状：账本是内部表，不直接把实体丢给前端。 */
    public record MessageView(Long id, String role, String content, String status,
                              String sessionId, String createdAt) {

        public static MessageView of(ChatMessage message) {
            return new MessageView(message.getId(), message.getRole().name(), message.getContent(),
                    message.getStatus().name(), message.getSessionId(),
                    String.valueOf(message.getCreatedAt()));
        }
    }
}
