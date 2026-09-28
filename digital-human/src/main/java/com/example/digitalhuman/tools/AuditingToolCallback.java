package com.example.digitalhuman.tools;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.ToolCallAuditRepository;

/**
 * 工具调用的横切包装：**超时边界 + 审计**。
 *
 * <p>为什么必须包一层：
 * <ul>
 *   <li>下游卡住不能把整个对话线程拖死——工具要有执行时间上限，超时后返回确定性结果给模型继续；</li>
 *   <li>每一次调用都要留痕：谁、哪个工具、什么参数、结果摘要、耗时、追踪号。</li>
 * </ul>
 *
 * <p>注意这里是「应用层」的确定性行为，不是提示词里的一句话。
 */
public class AuditingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolCallAuditRepository audits;
    private final ExecutorService executor;
    private final long timeoutMs;

    public AuditingToolCallback(ToolCallback delegate, ToolCallAuditRepository audits,
                                ExecutorService executor, long timeoutMs) {
        this.delegate = delegate;
        this.audits = audits;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        long startedAt = System.nanoTime();
        try {
            String result = callWithTimeout(toolInput, toolContext);
            audit(toolInput, toolContext, result, ToolCallAudit.Status.OK, elapsedMs(startedAt));
            return result;
        } catch (TimeoutException ex) {
            String message = "工具执行超过 " + timeoutMs + "ms，已被中止；请稍后重试或改用其它方式。";
            audit(toolInput, toolContext, message, ToolCallAudit.Status.TIMEOUT, elapsedMs(startedAt));
            return message;
        } catch (Exception ex) {
            String message = failureSummary(ex);
            audit(toolInput, toolContext, message, ToolCallAudit.Status.ERROR, elapsedMs(startedAt));
            return message;
        }
    }

    /**
     * 异常信息可能为空（远程工具断连时 MCP 客户端就会抛一个没有 message 的异常），
     * 那时至少要留下异常类型——否则审计里只剩「工具执行失败：null」，等于没留。
     */
    private static String failureSummary(Throwable ex) {
        String detail = ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = ex.getClass().getName();
        }
        return "工具执行失败：" + detail;
    }

    private String callWithTimeout(String toolInput, ToolContext toolContext) throws Exception {
        Future<String> future = executor.submit(() -> delegate.call(toolInput, toolContext));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            throw cause instanceof Exception checked ? checked : ex;
        }
    }

    private void audit(String toolInput, ToolContext context, String result,
                       ToolCallAudit.Status status, long elapsedMs) {
        audits.save(new ToolCallAudit(
                longValue(context, ToolContextKeys.PROJECT_ID),
                stringValue(context, ToolContextKeys.CONVERSATION_ID, null),
                stringValue(context, ToolContextKeys.SESSION_ID, null),
                longValue(context, ToolContextKeys.USER_ID),
                delegate.getToolDefinition().name(),
                toolInput == null ? "{}" : toolInput,
                truncate(result),
                status,
                elapsedMs,
                stringValue(context, ToolContextKeys.TRACE_ID, "no-trace")));
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static Long longValue(ToolContext context, String key) {
        Object value = context == null ? null : context.getContext().get(key);
        return value instanceof Long id ? id : null;
    }

    private static String stringValue(ToolContext context, String key, String fallback) {
        Object value = context == null ? null : context.getContext().get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
