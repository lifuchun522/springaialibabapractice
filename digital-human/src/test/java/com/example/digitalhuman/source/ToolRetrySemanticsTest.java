package com.example.digitalhuman.source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.InterceptorChain;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第 14 掌：把「工具失败重试」这条行为钉到 v1.1.2.2 的源码行上，并给出最小复现。
 *
 * <p>被验证的结论只有一句：**`ToolRetryInterceptor` 只对「抛异常」重试；
 * 如果内层先有一个把异常转成消息的拦截器，重试永远不触发。**
 * 源码依据（v1.1.2.2，行号由 scripts/locate-source.ps1 复算）：
 * <ul>
 *   <li>{@code ToolRetryInterceptor#interceptToolCall} 第 88 行 {@code return handler.call(request);}
 *       在 try 里，第 90 行 {@code catch (Exception e)} 才开始重试判断——
 *       也就是说「返回一个失败响应」和「抛异常」在这里是两种命运；</li>
 *   <li>框架自带的 {@code ToolErrorInterceptor}（第 24-30 行）恰好把异常转成
 *       {@code "Tool failed: ..."} 的**正常响应**；</li>
 *   <li>{@code InterceptorChain#chainToolInterceptors} 第 83-91 行：**先声明的在外层**。</li>
 * </ul>
 * 三件事拼起来的后果：{@code [retry, error]} 的顺序下，error 在内层把异常吃掉了，
 * retry 看到的是正常返回，于是工具只被调用一次。这不是框架 bug，是**顺序契约没有写清楚**。
 *
 * <p>上游在 main 上已经改了这件事（把「非成功响应」也当成失败抛出去重试，
 * 并把 maxRetries 换成 maxAttempts），但**我们锁的 1.1.2.2 没有**——
 * 这正是「tag 才是事实源」的实例。
 */
class ToolRetrySemanticsTest {

    private static ToolCallRequest request() {
        return ToolCallRequest.builder()
                .toolName("flaky_tool")
                .arguments("{}")
                .toolCallId("call-1")
                .build();
    }

    /** 一个总是抛异常的工具，并记录它被调用了多少次。 */
    private static ToolCallHandler alwaysFailing(AtomicInteger counter) {
        return req -> {
            counter.incrementAndGet();
            throw new IllegalStateException("下游 503");
        };
    }

    private static ToolCallHandler chainOf(ToolCallHandler baseHandler,
                                           com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor... interceptors) {
        return InterceptorChain.chainToolInterceptors(List.of(interceptors), baseHandler);
    }

    @Test
    @DisplayName("errorOuterRetryInner_shouldRetryEveryThrownFailure")
    void errorOuterRetryInner_shouldRetryEveryThrownFailure() {
        AtomicInteger calls = new AtomicInteger();
        // 先声明的在外层（InterceptorChain 第 83-91 行）：error 在外、retry 在内
        ToolCallHandler handler = chainOf(alwaysFailing(calls),
                ToolErrorInterceptor.builder().build(),                  // 外层
                ToolRetryInterceptor.builder().maxRetries(2).build());   // 内层

        ToolCallResponse response = handler.call(request());

        // 内层的 retry 先看到异常 → 三次尝试 → 耗尽后自己返回一句可读的失败
        assertThat(calls.get()).isEqualTo(3);
        assertThat(response.getResult()).contains("Tool call failed after 3 attempts");
    }

    @Test
    @DisplayName("retryOuterErrorInner_shouldSwallowTheExceptionAndKillRetry")
    void retryOuterErrorInner_shouldSwallowTheExceptionAndKillRetry() {
        AtomicInteger calls = new AtomicInteger();
        // 反过来：retry 在外层，error 在内层——error 先把异常转成了正常响应
        ToolCallHandler handler = chainOf(alwaysFailing(calls),
                ToolRetryInterceptor.builder().maxRetries(2).build(),   // 外层
                ToolErrorInterceptor.builder().build());                 // 内层：把异常吃掉了

        ToolCallResponse response = handler.call(request());

        // 关键断言：外层的重试**一次都没有发生**——它看到的是「正常返回」
        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getResult()).contains("Tool failed");
        // 一句话记住顺序契约：**重试必须在「把异常转成消息」的那个拦截器里面**。
        // 我们第 9 掌踩的就是这个坑：审计包装把失败转成字符串，于是外层重试成了摆设。
    }

    @Test
    @DisplayName("nonSuccessResponse_shouldNotBeRetriedInLockedVersion")
    void nonSuccessResponse_shouldNotBeRetriedInLockedVersion() {
        AtomicInteger calls = new AtomicInteger();
        // 工具「不抛异常、只返回一个失败响应」——很多包装器（包括我们第 9 掌的审计包装）就是这么写的
        ToolCallHandler failingByResponse = req -> {
            calls.incrementAndGet();
            return ToolCallResponse.of(req.getToolCallId(), req.getToolName(), "工具执行失败：下游 503");
        };
        ToolCallHandler handler = chainOf(failingByResponse,
                ToolRetryInterceptor.builder().maxRetries(2).build());

        ToolCallResponse response = handler.call(request());

        // 1.1.2.2 的行为：**不重试**（只重试抛异常）
        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getResult()).contains("工具执行失败");
        // 上游 main 已改成「非成功响应也当失败重试，且把 maxRetries 换成 maxAttempts」——
        // 也就是说这条断言的期望值在 main 上会变成 3；升级依赖时必须一并改这里的期望
    }
}
