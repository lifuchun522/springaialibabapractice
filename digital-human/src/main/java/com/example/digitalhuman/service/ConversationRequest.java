package com.example.digitalhuman.service;

import java.util.UUID;

/**
 * 一次对话请求。
 *
 * @param projectId  数字人项目（决定人设、模型参数与工具作用域）
 * @param sessionId  前端会话标识（同一项目下区分浏览器会话）
 * @param userId     登录用户；运行页允许匿名访问时为 null
 * @param text       用户输入
 * @param allowWrite 是否把写工具交给模型——默认 false，写能力必须被显式请求
 * @param traceId    追踪号：贯穿工具审计与日志，为空时自动生成
 */
public record ConversationRequest(Long projectId, String sessionId, Long userId, String text,
                                  boolean allowWrite, String traceId) {

    public ConversationRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("参数 text/q 不能为空");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }

    /** 只读对话（默认）：不把写工具交给模型。 */
    public ConversationRequest(Long projectId, String sessionId, Long userId, String text) {
        this(projectId, sessionId, userId, text, false, null);
    }

    public ConversationRequest(Long projectId, String sessionId, Long userId, String text, boolean allowWrite) {
        this(projectId, sessionId, userId, text, allowWrite, null);
    }

    public ConversationId conversationId() {
        return ConversationId.of(userId, projectId, sessionId);
    }
}
