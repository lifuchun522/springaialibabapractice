package com.example.digitalhuman.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 对话消息账本：只追加，不修改。
 *
 * <p>它和 ChatMemory 的分工是本掌的核心：Memory 是「模型短期看什么」（可丢、可重建、窗口可调），
 * 账本是「产品历史记了什么」（必须查得回、必须能解释取消与失败）。
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    public enum Role {
        USER, ASSISTANT
    }

    public enum Status {
        /** 正常完成（对 USER 消息即为已投递）。 */
        COMPLETED,
        /** 客户端断开：半截消息也要留档，不能假装什么都没发生。 */
        CANCELLED,
        /** 上游失败：留错误摘要，便于事后解释这条消息为什么没有下文。 */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "conversation_id", nullable = false, length = 160)
    private String conversationId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
        // JPA 需要
    }

    public ChatMessage(Long projectId, String conversationId, String sessionId, Long userId,
                       Role role, String content, Status status) {
        this.projectId = projectId;
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
