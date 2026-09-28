package com.example.digitalhuman.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次 Agent 运行的事件时间线。
 *
 * <p>为什么把它做成一等公民：引入 Agent 框架的第一收益不是「答案更好」，而是**可观测性**。
 * 如果升级之后仍然只能看到「输入一句话、输出一段文本」，这次升级等于没做。
 *
 * <p>所以一次请求会留下：Agent 启动 → 每一次模型调用 → 每一次工具调用 → 收尾。
 * 工具调用另有 tool_call_audit 表留痕（第 6 掌），这里负责把「顺序」串起来。
 */
public final class AgentTrace {

    private final String traceId;
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final List<String> events = new CopyOnWriteArrayList<>();

    public AgentTrace(String traceId) {
        this.traceId = traceId;
    }

    public String traceId() {
        return traceId;
    }

    public int modelCalls() {
        return modelCalls.get();
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public void event(String node) {
        events.add(node);
    }

    /** 模型调用计数由 ModelInterceptor 触发，用来验证「单次请求有硬上界」。 */
    public int countModelCall() {
        int index = modelCalls.incrementAndGet();
        events.add("model#" + index);
        return index;
    }

    public void toolCalled(String toolName) {
        events.add("tool:" + toolName);
    }

    /** 同一线程内传递当前运行（ReactAgent.call 是同步调用）。 */
    private static final ThreadLocal<AgentTrace> CURRENT = new ThreadLocal<>();

    static void bind(AgentTrace trace) {
        CURRENT.set(trace);
    }

    static void unbind() {
        CURRENT.remove();
    }

    static AgentTrace current() {
        return CURRENT.get();
    }
}
