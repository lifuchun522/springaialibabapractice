package com.example.digitalhuman.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.digitalhuman.observability.RequestContext;

/**
 * 第 17 掌：**身份只能有一个来源**。
 *
 * <p>这条用例来自一次真实验收的对比：一次请求的响应头 traceId 是 32 位 {@code 9a3abf6d…}，
 * 而同一请求写进 {@code tool_call_audit} 的 trace_id 是另一个 12 位号 {@code c7c2b3418b6c}。
 * 两套号并存意味着「日志里的链路」和「工具审计的事实」永远 join 不上——
 * 而工具审计恰恰是「模型到底调了什么」的唯一真相。
 *
 * <p>修法就是让 `ConversationRequest` 继承请求上下文里的 traceId；这里把它钉住。
 */
class ConversationRequestTraceTest {

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("有请求上下文时继承上下文 traceId：工具审计与日志从此是同一个号")
    void shouldInheritTraceIdFromRequestContext() {
        RequestContext.bind(new RequestContext("9a3abf6d8b8f4f0aa31ebbf02755d729", "1", "s-1", "1:s-1"));

        ConversationRequest request = new ConversationRequest(1L, "s-1", 7L, "你好");

        assertThat(request.traceId()).isEqualTo("9a3abf6d8b8f4f0aa31ebbf02755d729");
    }

    @Test
    @DisplayName("没有上下文（定时任务、内部调用）才自己生成，且格式不变")
    void shouldGenerateTraceIdWhenNoContext() {
        ConversationRequest request = new ConversationRequest(1L, "s-1", 7L, "你好");

        assertThat(request.traceId()).hasSize(12).matches("[0-9a-f]{12}");
    }

    @Test
    @DisplayName("显式传入的 traceId 优先：跨进程调用要能带上游的号")
    void explicitTraceIdWins() {
        RequestContext.bind(new RequestContext("context-trace", "1", "s-1", "1:s-1"));

        ConversationRequest request = new ConversationRequest(1L, "s-1", 7L, "你好", false, "upstream-trace");

        assertThat(request.traceId()).isEqualTo("upstream-trace");
    }

    @Test
    @DisplayName("上下文里是 unknown（非 HTTP 入口占位）时不要污染审计表")
    void unknownContextFallsBackToGeneratedId() {
        RequestContext.bind(new RequestContext("unknown", "unknown", "unknown", "unknown:unknown"));

        ConversationRequest request = new ConversationRequest(1L, "s-1", 7L, "你好");

        assertThat(request.traceId()).hasSize(12).isNotEqualTo("unknown");
    }
}
