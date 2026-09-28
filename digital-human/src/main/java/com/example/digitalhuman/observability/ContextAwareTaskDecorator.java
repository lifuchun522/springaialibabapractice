package com.example.digitalhuman.observability;

import java.util.concurrent.Callable;

import org.springframework.core.task.TaskDecorator;

/**
 * 线程池上下文传播（第 17 掌的第二个坑，也是最容易在第 06 章那种排查里踩到的）。
 *
 * <p>{@link RequestContext} 与 {@link SpanScope} 都是 ThreadLocal，
 * **ThreadLocal 不随线程池传播**：工具执行在线程池里跑、SSE 由异步执行器托管、
 * Graph 节点在异步线程里推进——只要跨过一次线程池，身份就空了，日志里那几行又变成互不相认。
 *
 * <p>要传播的是**两层**，这一个是实测补上的：
 * <ul>
 *   <li>{@link RequestContext}：traceId / projectId / sessionId / threadId，
 *       决定「这是哪一次请求」；</li>
 *   <li>{@link SpanScope}：当前片段，决定「这一段挂在谁下面」。
 *       只传身份不传片段，工具片段会在调用树上变成第二个根——
 *       现象是「工具确实跑了，但看不出它是在哪一次模型调用里被调用的」。</li>
 * </ul>
 *
 * <p>传播的语义要写清楚，否则会带来串号：
 * <ul>
 *   <li><b>在提交任务时捕获</b>，而不是在任务执行时读取——执行时读到的已经是别人的；</li>
 *   <li>执行完 <b>必须恢复</b>上一层：池里的线程会被复用，不恢复等于把上一个请求的身份留给下一个请求；</li>
 *   <li>只传播身份与片段，不传播业务状态；观测层不替业务做事务或会话管理。</li>
 * </ul>
 */
public class ContextAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        RequestContext captured = RequestContext.current();
        SpanScope.Capture span = SpanScope.capture();
        return () -> runWithContext(captured, span, runnable);
    }

    /** 给 {@code ExecutorService#submit(Callable)} 这类路径用。 */
    public static <T> Callable<T> decorateCallable(Callable<T> callable) {
        RequestContext captured = RequestContext.current();
        SpanScope.Capture span = SpanScope.capture();
        return () -> {
            RequestContext previous = RequestContext.current();
            SpanScope.Capture previousSpan = SpanScope.capture();
            RequestContext.bind(captured);
            SpanScope.restore(span);
            try {
                return callable.call();
            } finally {
                restore(previous, previousSpan);
            }
        };
    }

    /** 给 {@code ExecutorService#execute/submit(Runnable)} 路径用。 */
    public static Runnable decorateRunnable(Runnable runnable) {
        RequestContext captured = RequestContext.current();
        SpanScope.Capture span = SpanScope.capture();
        return () -> runWithContext(captured, span, runnable);
    }

    private static void runWithContext(RequestContext captured, SpanScope.Capture span, Runnable runnable) {
        RequestContext previous = RequestContext.current();
        SpanScope.Capture previousSpan = SpanScope.capture();
        RequestContext.bind(captured);
        SpanScope.restore(span);
        try {
            runnable.run();
        } finally {
            restore(previous, previousSpan);
        }
    }

    private static void restore(RequestContext previous, SpanScope.Capture previousSpan) {
        if (previous == null) {
            RequestContext.clear();
        } else {
            RequestContext.bind(previous);
        }
        SpanScope.restore(previousSpan);
    }
}
