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
 * @param traceId    追踪号：贯穿工具审计与日志；为空时**继承当前请求上下文**，上下文也没有才自己生成
 */
public record ConversationRequest(Long projectId, String sessionId, Long userId, String text,
                                  boolean allowWrite, String traceId) {

    public ConversationRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("参数 text/q 不能为空");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = resolveTraceId();
        }
    }

    /**
     * 追踪号的来源优先级（第 17 掌真实验收后改的）。
     *
     * <p>原来这里无脑生成一个 12 位随机号，于是系统里**同时存在两套身份**：
     * HTTP 过滤器那套 32 位 traceId（日志、诊断面、响应头用它），
     * 和这里生成的 12 位号（工具审计表用它）。实测证据：一次请求的响应头 traceId 是
     * {@code 9a3abf6d…}，而同一请求的工具审计行写的是 {@code c7c2b3418b6c}——
     * 两张表因此**永远 join 不上**，而工具审计恰恰是「模型到底调了什么」的唯一真相。
     *
     * <p>修法就是这一行顺序：先继承请求上下文，上下文没有（定时任务、内部调用等非 HTTP 入口）
     * 才退回自己生成。身份只有一个来源，才谈得上「一条链」。
     */
    private static String resolveTraceId() {
        com.example.digitalhuman.observability.RequestContext current =
                com.example.digitalhuman.observability.RequestContext.current();
        if (current != null && !"unknown".equals(current.traceId())) {
            return current.traceId();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
