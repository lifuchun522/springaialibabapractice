package com.example.digitalhuman.tools;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.ToolCallAuditRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工具执行的超时边界与审计留痕：下游卡住不能把对话线程拖死，且每次调用都要留痕。 */
class AuditingToolCallbackTest {

    private final ToolCallAuditRepository audits = mock(ToolCallAuditRepository.class);

    private static ToolCallback callback(String name, java.util.function.Supplier<String> body) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("测试工具").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return body.get();
            }
        };
    }

    private static ToolContext context() {
        return new ToolContext(java.util.Map.of(
                ToolContextKeys.PROJECT_ID, 7L,
                ToolContextKeys.USER_ID, 3L,
                ToolContextKeys.SESSION_ID, "s1",
                ToolContextKeys.CONVERSATION_ID, "3:7:s1",
                ToolContextKeys.TRACE_ID, "trace-abc"));
    }

    @Test
    @DisplayName("call_shouldReturnResultAndWriteAuditWithTraceId")
    void call_shouldReturnResultAndWriteAuditWithTraceId() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("slowOrFast", () -> "ok"), audits, executor, 1000);

            assertThat(wrapped.call("{\"a\":1}", context())).isEqualTo("ok");

            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            verify(audits).save(captor.capture());
            ToolCallAudit audit = captor.getValue();
            assertThat(audit.getToolName()).isEqualTo("slowOrFast");
            assertThat(audit.getStatus()).isEqualTo(ToolCallAudit.Status.OK);
            assertThat(audit.getProjectId()).isEqualTo(7L);
            assertThat(audit.getCallerUserId()).isEqualTo(3L);
            assertThat(audit.getTraceId()).isEqualTo("trace-abc");
            assertThat(audit.getArguments()).isEqualTo("{\"a\":1}");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldCutOffSlowToolAndKeepConversationAlive")
    void call_shouldCutOffSlowToolAndKeepConversationAlive() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger finished = new AtomicInteger();
        try {
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("stuckTool", () -> {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ex) {
                            // 被超时掐断：直接返回，不再往下走，也就不会产生「迟到」的副作用
                            Thread.currentThread().interrupt();
                            return "已被中断";
                        }
                        finished.incrementAndGet();
                        return "太晚了";
                    }),
                    audits, executor, 200);

            long startedAt = System.currentTimeMillis();
            String result = wrapped.call("{}", context());
            long elapsed = System.currentTimeMillis() - startedAt;

            assertThat(result).contains("已被中止");
            assertThat(elapsed).isLessThan(2000);   // 不能被下游拖满 5 秒
            assertThat(finished.get()).isZero();     // 超时后被取消，不会有迟到的副作用

            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            verify(audits).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ToolCallAudit.Status.TIMEOUT);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldRecordErrorStatusInsteadOfThrowingIntoConversation")
    void call_shouldRecordErrorStatusInsteadOfThrowingIntoConversation() {
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("boomTool", () -> {
                        throw new IllegalStateException("下游 500");
                    }), audits, executor, 1000);

            String result = wrapped.call("{}", context());

            assertThat(result).contains("工具执行失败").contains("下游 500");
            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            verify(audits).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ToolCallAudit.Status.ERROR);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldFallBackToExceptionTypeWhenMessageIsNull")
    void call_shouldFallBackToExceptionTypeWhenMessageIsNull() {
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 远程工具断连时 MCP 客户端会抛出没有 message 的异常：审计里不能只剩一个 null
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("nullMessageTool", () -> {
                        throw new IllegalStateException();
                    }), audits, executor, 1000);

            String result = wrapped.call("{}", context());

            assertThat(result).contains("工具执行失败").contains("IllegalStateException");
            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            verify(audits).save(captor.capture());
            assertThat(captor.getValue().getResultSummary()).contains("IllegalStateException");
            assertThat(captor.getValue().getStatus()).isEqualTo(ToolCallAudit.Status.ERROR);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldShowRootCauseWhenWrapperExceptionHasNoMessage")
    void call_shouldShowRootCauseWhenWrapperExceptionHasNoMessage() {
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 第 9 掌实测的形状：外层 ToolExecutionException 没有 message，
            // 真正有用的是最内层的「Connection refused」——只写包装类型等于没写
            // 第 9 掌实测的形状：外层包装异常没有 message，真正有用的
            // 「Connection refused」藏在 cause 链最里面——只写外层类型等于没写
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("remoteTool", () -> {
                        throw new IllegalStateException((String) null,
                                new java.net.ConnectException("Connection refused: localhost/127.0.0.1:8082"));
                    }), audits, executor, 1000);

            String result = wrapped.call("{}", context());

            assertThat(result).contains("工具执行失败")
                    .contains("IllegalStateException")
                    .contains("ConnectException")
                    .contains("Connection refused");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldKeepAuditEvenWhenRawContextIsMissing")
    void call_shouldKeepAuditEvenWhenRawContextIsMissing() {
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("noCtxTool", () -> "ok"), audits, executor, 1000);

            assertThat(wrapped.call("{}", null)).isEqualTo("ok");

            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            verify(audits).save(captor.capture());
            assertThat(captor.getValue().getTraceId()).isEqualTo("no-trace");
            assertThat(captor.getValue().getProjectId()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- 下面是第 9 掌补上的重试边界：失败要能重试，重试必须有上限 ----

    @Test
    @DisplayName("call_shouldRetryUntilSuccessAndAuditOnlyTheSuccessfulAttempt")
    void call_shouldRetryUntilSuccessAndAuditOnlyTheSuccessfulAttempt() {
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();
        try {
            // 前两次抖动、第三次成功：这是「下游 503 偶发」的典型形状
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("flakyTool", () -> {
                        if (attempts.incrementAndGet() <= 2) {
                            throw new IllegalStateException("下游 503");
                        }
                        return "好了";
                    }), audits, executor, 1000, 2);

            assertThat(wrapped.call("{}", context())).isEqualTo("好了");
            assertThat(attempts.get()).isEqualTo(3);   // 1 次原始调用 + 2 次重试

            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            verify(audits, org.mockito.Mockito.times(3)).save(captor.capture());
            // 失败的每一次尝试都留痕，最后一次才是 OK：重试了几次是能从表里数出来的
            assertThat(captor.getAllValues()).extracting(ToolCallAudit::getStatus)
                    .containsExactly(ToolCallAudit.Status.ERROR, ToolCallAudit.Status.ERROR, ToolCallAudit.Status.OK);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldGiveUpAfterMaxRetriesAndReturnReadableMessage")
    void call_shouldGiveUpAfterMaxRetriesAndReturnReadableMessage() {
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();
        try {
            // 一直失败：重试必须有上限，否则一次失败会变成无限等待
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("alwaysDownTool", () -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("下游 500");
                    }), audits, executor, 1000, 2);

            String result = wrapped.call("{}", context());

            // 重试耗尽后仍然是「一句话」，不是异常——模型能读到失败原因并继续回答
            assertThat(result).contains("工具执行失败").contains("下游 500");
            assertThat(attempts.get()).isEqualTo(3);
            verify(audits, org.mockito.Mockito.times(3)).save(any(ToolCallAudit.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("call_shouldNotRetryTimeoutBecauseWaitingLongerHelpsNobody")
    void call_shouldNotRetryTimeoutBecauseWaitingLongerHelpsNobody() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger attempts = new AtomicInteger();
        try {
            ToolCallback wrapped = new AuditingToolCallback(
                    callback("stuckToolWithRetry", () -> {
                        attempts.incrementAndGet();
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        return "太晚了";
                    }), audits, executor, 200, 2);

            long startedAt = System.currentTimeMillis();
            String result = wrapped.call("{}", context());
            long elapsed = System.currentTimeMillis() - startedAt;

            assertThat(result).contains("已被中止");
            assertThat(attempts.get()).isEqualTo(1);      // 超时不重试
            assertThat(elapsed).isLessThan(2000);         // 也就不会等 3 个超时周期
        } finally {
            executor.shutdownNow();
        }
    }
}
