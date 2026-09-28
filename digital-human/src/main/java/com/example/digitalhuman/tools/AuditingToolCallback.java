package com.example.digitalhuman.tools;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.ToolCallAuditRepository;

/**
 * 工具调用的横切包装：**超时边界 + 有限重试 + 审计**。
 *
 * <p>为什么必须包一层：
 * <ul>
 *   <li>下游卡住不能把整个对话线程拖死——工具要有执行时间上限，超时后返回确定性结果给模型继续；</li>
 *   <li>失败要重试（下游 503 这类抖动是常态），但重试必须有次数上限——无界重试等于把一次失败换成一堆失败；</li>
 *   <li>每一次调用都要留痕：谁、哪个工具、什么参数、结果摘要、耗时、追踪号。<b>失败的每一次尝试都单独留痕</b>，
 *       所以「重试过几次」在审计表里是能数出来的，不用靠日志猜。</li>
 * </ul>
 *
 * <p>注意这里是「应用层」的确定性行为，不是提示词里的一句话。
 *
 * <p><b>为什么重试在这一层，而不是框架的重试拦截器里</b>（第 9 掌的结论）：
 * 这一层已经把失败转成「可读结果」交给模型了，所以挂在更外层的重试拦截器<b>永远看不到失败</b>——
 * 一个配了但不可能触发的通道比没有更糟。失败策略只能有一个主人，就放在工具边界。
 */
public class AuditingToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(AuditingToolCallback.class);

    /** 不重试：保持第 6 掌「一次调用、一次留痕」的语义，测试与老路径都用这个构造。 */
    public static final int NO_RETRY = 0;

    private final ToolCallback delegate;
    private final ToolCallAuditRepository audits;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final int maxRetries;
    /** 观测入口；为 null 表示这条链路不采集（单测与不关心观测的调用方）。 */
    private final com.example.digitalhuman.observability.ObservedOperation observed;

    public AuditingToolCallback(ToolCallback delegate, ToolCallAuditRepository audits,
                                ExecutorService executor, long timeoutMs) {
        this(delegate, audits, executor, timeoutMs, NO_RETRY, null);
    }

    /**
     * @param maxRetries 失败后的重试次数；总尝试次数 = maxRetries + 1。
     *                   <b>超时不算失败</b>：超时说明下游已经慢到不值得再等，重试只会把等待翻倍。
     */
    public AuditingToolCallback(ToolCallback delegate, ToolCallAuditRepository audits,
                                ExecutorService executor, long timeoutMs, int maxRetries) {
        this(delegate, audits, executor, timeoutMs, maxRetries, null);
    }

    /** 第 17 掌加的构造：带上观测入口，让工具调用自动进调用树与指标。 */
    public AuditingToolCallback(ToolCallback delegate, ToolCallAuditRepository audits,
                                ExecutorService executor, long timeoutMs, int maxRetries,
                                com.example.digitalhuman.observability.ObservedOperation observed) {
        this.delegate = delegate;
        this.audits = audits;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.maxRetries = Math.max(0, maxRetries);
        this.observed = observed;
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
        // 第 17 掌：工具是「必经之路」之一，埋点站在这里而不是各业务代码里——
        // 只要工具真的被执行，就一定有记录（含失败分类 TOOL_ERROR）。
        // observed 允许为空：单测与老路径不注入观测，既不污染测试也不想让观测变成必需依赖。
        if (observed == null) {
            return executeWithRetry(toolInput, toolContext);
        }
        return observed.observe("genai.tool", "tool",
                java.util.Map.of("tool.name", delegate.getToolDefinition().name()),
                () -> executeWithRetry(toolInput, toolContext));
    }

    private String executeWithRetry(String toolInput, ToolContext toolContext) {
        for (int attempt = 0; ; attempt++) {
            long startedAt = System.nanoTime();
            try {
                String result = callWithTimeout(toolInput, toolContext);
                audit(toolInput, toolContext, result, ToolCallAudit.Status.OK, elapsedMs(startedAt));
                // 第 17 掌：这一行是在工具执行池线程上打出来的。它和入口那行日志共享同一个 traceId，
                // 就是「身份跨线程池传播」最直观的证据（配置见 ContextAwareExecutorService）。
                log.info("[tool] {} 执行完成 status=OK elapsed={}ms", delegate.getToolDefinition().name(),
                        elapsedMs(startedAt));
                return result;
            } catch (TimeoutException ex) {
                String message = "工具执行超过 " + timeoutMs + "ms，已被中止；请稍后重试或改用其它方式。";
                audit(toolInput, toolContext, message, ToolCallAudit.Status.TIMEOUT, elapsedMs(startedAt));
                return message;
            } catch (Exception ex) {
                String message = failureSummary(ex);
                // 每一次失败的尝试都留痕：重试次数在审计表里可数
                audit(toolInput, toolContext, message, ToolCallAudit.Status.ERROR, elapsedMs(startedAt));
                if (attempt >= maxRetries) {
                    // 重试耗尽：把「当前这个工具不可用」这个事实交给模型，由它决定怎么对用户说
                    return message;
                }
                log.warn("[tool] 第 {}/{} 次调用失败，将重试 tool={} reason={}",
                        attempt + 1, maxRetries + 1, delegate.getToolDefinition().name(), message);
            }
        }
    }

    /**
     * 异常信息可能为空（远程工具断连时 MCP 客户端就会抛一个没有 message 的异常），
     * 那时至少要留下异常类型——否则审计里只剩「工具执行失败：null」，等于没留。
     *
     * <p>第 9 掌补上根因：远程断连时外层往往是一个没有 message 的包装异常
     * （实测是 {@code ToolExecutionException}），只写包装类型仍然等于没写——
     * 真正有用的「Connection refused」在 cause 链最里面。所以往根因走一层，把根因类型与信带出来。
     */
    private static String failureSummary(Throwable ex) {
        String detail = ex.getMessage();
        if (detail != null && !detail.isBlank()) {
            return "工具执行失败：" + detail;
        }
        StringBuilder summary = new StringBuilder("工具执行失败：").append(ex.getClass().getName());
        Throwable root = rootCauseOf(ex);
        if (root != null) {
            summary.append("（根因 ").append(root.getClass().getSimpleName());
            String rootDetail = root.getMessage();
            if (rootDetail != null && !rootDetail.isBlank()) {
                summary.append(": ").append(rootDetail);
            }
            summary.append('）');
        }
        return summary.toString();
    }

    /** 最内层 cause；异常没有 cause 时返回 null（避免把「自己」当成根因写两遍）。 */
    private static Throwable rootCauseOf(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == ex ? null : current;
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
