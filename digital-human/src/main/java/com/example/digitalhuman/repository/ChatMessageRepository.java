package com.example.digitalhuman.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.digitalhuman.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 第 18 掌：Memory 要从「进程内」搬到「库内」，所以需要按会话键读历史。
     *
     * <p>注意会话键用的是 {@code conversation_id}（第 5 掌的 conversationId 三层键），
     * 而不是 {@code session_id}——同一个浏览器会话在不同登录用户下是不同的会话，
     * 用 sessionId 读会让两个用户的上下文串在一起。
     */
    List<ChatMessage> findByConversationIdOrderByIdAsc(String conversationId);

    /** 供诊断与运维查看有哪些会话（Memory 的实现需要它列出会话键）。 */
    @Query("select distinct m.conversationId from ChatMessage m")
    List<String> findDistinctConversationIds();

    /** 产品历史按 projectId + sessionId 查，与模型看到的 Memory 窗口互不相干。 */
    List<ChatMessage> findByProjectIdAndSessionIdOrderByIdAsc(Long projectId, String sessionId);
}
