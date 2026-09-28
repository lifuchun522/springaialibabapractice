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
 * 工具调用审计：谁在哪个项目/会话里，让模型调了哪个工具、传了什么参数、结果如何、花了多久。
 *
 * <p>没有这张表，「模型说的数字从哪来」就只能靠猜——而工具是自然语言驱动的入口，
 * 入口必须留痕。
 */
@Entity
@Table(name = "tool_call_audit")
public class ToolCallAudit {

    public enum Status {
        OK,
        /** 工具自己抛出异常。 */
        ERROR,
        /** 超过执行时间上限，被应用层掐断。 */
        TIMEOUT,
        /** 应用层拒绝执行（例如写操作缺少确认令牌）。 */
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "conversation_id", length = 160)
    private String conversationId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "caller_user_id")
    private Long callerUserId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "arguments", nullable = false, columnDefinition = "TEXT")
    private String arguments;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "elapsed_ms", nullable = false)
    private long elapsedMs;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ToolCallAudit() {
        // JPA 需要
    }

    public ToolCallAudit(Long projectId, String conversationId, String sessionId, Long callerUserId,
                         String toolName, String arguments, String resultSummary, Status status,
                         long elapsedMs, String traceId) {
        this.projectId = projectId;
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.callerUserId = callerUserId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.resultSummary = resultSummary;
        this.status = status;
        this.elapsedMs = elapsedMs;
        this.traceId = traceId;
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

    public Long getCallerUserId() {
        return callerUserId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public Status getStatus() {
        return status;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
