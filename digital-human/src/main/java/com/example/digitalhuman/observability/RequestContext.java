package com.example.digitalhuman.observability;

import org.slf4j.MDC;

/**
 * 请求身份四元组（第 17 掌）。
 *
 * <p>一次对话要穿过 ChatClient、Memory、Tool、RAG、ReactAgent 多轮循环，还可能把子任务交给远程 Agent。
 * 线程会切、进程会换，于是「这几行日志是不是同一次请求」就变成了靠时间戳对表——
 * 而时间戳在并发下什么都不是。这个类就是那根线。
 *
 * <p>四元组缺一不可，各自回答一个不同的问题：
 * <ul>
 *   <li>{@code traceId}：**这一次**请求。技术定位用它，跨线程、跨进程传的也是它；</li>
 *   <li>{@code projectId}：哪个数字人。面板聚合与权限隔离的维度；</li>
 *   <li>{@code sessionId}：用户视角的「这一次对话」；</li>
 *   <li>{@code threadId}：Agent 的对话线程，等于 Memory/图检查点里的会话键——
 *       没有它，你能看到「模型调用失败了」，但读不回那条会话的历史。</li>
 * </ul>
 *
 * <p>绑定方式刻意做成 {@link ThreadLocal} + {@link MDC} 双写：
 * ThreadLocal 给代码读，MDC 给日志框架读。两者必须一起清，
 * 否则线程池里的下一个任务会继承上一个请求的身份——这类串号比丢号更难查。
 */
public record RequestContext(String traceId, String projectId, String sessionId, String threadId) {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_PROJECT_ID = "X-Project-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_PROJECT_ID = "projectId";
    public static final String MDC_SESSION_ID = "sessionId";
    public static final String MDC_THREAD_ID = "threadId";

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    public static RequestContext current() {
        return HOLDER.get();
    }

    /** 绑定到当前线程，并同步写进 MDC（日志里那一行身份就是从这里来的）。 */
    public static void bind(RequestContext context) {
        HOLDER.set(context);
        if (context == null) {
            clearMdc();
            return;
        }
        MDC.put(MDC_TRACE_ID, context.traceId());
        MDC.put(MDC_PROJECT_ID, context.projectId());
        MDC.put(MDC_SESSION_ID, context.sessionId());
        MDC.put(MDC_THREAD_ID, context.threadId());
    }

    public static void clear() {
        HOLDER.remove();
        clearMdc();
    }

    private static void clearMdc() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_PROJECT_ID);
        MDC.remove(MDC_SESSION_ID);
        MDC.remove(MDC_THREAD_ID);
    }

    /** 目前对外暴露的身份摘要，用于诊断接口与日志（不返回 null，缺省写 unknown）。 */
    public static RequestContext orUnknown() {
        RequestContext context = current();
        return context == null ? new RequestContext("unknown", "unknown", "unknown", "unknown") : context;
    }
}
