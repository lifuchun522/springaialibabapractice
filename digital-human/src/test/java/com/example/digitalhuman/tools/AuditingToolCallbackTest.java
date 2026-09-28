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
}
