package com.example.digitalhuman.observability;

/**
 * 当前片段的传播载体（第 17 掌）。
 *
 * <p>为什么单独拎出来：调用树的父子关系靠「我现在在哪一段里」来判断，
 * 而这个判断必须跟着任务一起跨线程池——工具执行在 {@code tool-exec} 池里跑、
 * SSE 在异步执行器里跑。只传播 {@link RequestContext}（traceId 那一层）是不够的：
 * traceId 对了，但父节点丢了，工具片段就会变成树上的第二个根，
 * 表现是「都跑过，但看不出谁在谁里面」——实测撞到过。
 *
 * <p>所以身份和片段是两个层次，各有一个 ThreadLocal，也各要一次传播。
 */
public final class SpanScope {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private SpanScope() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static void set(String spanId) {
        if (spanId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(spanId);
        }
    }

    /** 捕获当前片段供跨线程使用。 */
    public static Capture capture() {
        return new Capture(CURRENT.get());
    }

    public static void restore(Capture capture) {
        set(capture == null ? null : capture.spanId());
    }

    /** 不可变的片段快照。 */
    public record Capture(String spanId) {
    }
}
