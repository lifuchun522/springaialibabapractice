package com.example.digitalhuman.service;

/**
 * 一次对话请求。
 *
 * @param projectId 数字人项目（决定人设与模型参数）
 * @param sessionId 前端会话标识（同一项目下区分浏览器会话）
 * @param userId    登录用户；运行页允许匿名访问时为 null
 * @param text      用户输入
 */
public record ConversationRequest(Long projectId, String sessionId, Long userId, String text) {

    public ConversationRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("参数 text/q 不能为空");
        }
    }

    public ConversationId conversationId() {
        return ConversationId.of(userId, projectId, sessionId);
    }
}
