package com.example.digitalhuman.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 调用树记录器（第 17 掌）：把每层的一段「开始—结束—结果」记下来，按 traceId 拼成树。
 *
 * <p>为什么需要它，而不是只看日志：日志擅长回答「发生了什么」，不擅长回答
 * **「这次请求死在哪一层」**。要回答后者，需要的是结构化事实：谁是谁的父节点、持续了多久、结果如何。
 * 有了父子关系，一次失败请求的还原就从「翻日志对齐时间戳」变成「按 traceId 拿一棵树」。
 *
 * <p>片段是「先开始、后结束」的：开始时就要拿到 spanId，子片段才能挂到它下面——
 * 如果等结束才记录，嵌套观测就只能得到一堆互不相连的根节点，树退化成一堆片段，
 * 那是「有埋点、没链路」，本掌开头批评的正是这个。
 *
 * <p>三条边界：
 * <ul>
 *   <li><b>容量有上限</b>（默认保留最近 500 个 traceId 的片段）：它是诊断缓冲，不是存储。
 *       长期留存该走 OTLP 后端与指标聚合，这里只保证「刚出事的那几次能立刻看见」；</li>
 *   <li><b>只记录身份与结果，不记录正文</b>：用户输入与模型输出可能含敏感信息，
 *       诊断面默认不缓存它们——要排查内容问题，用账本与审计表，它们的访问是要授权的；</li>
 *   <li><b>线程安全</b>：同一 traceId 的片段来自模型线程、工具池线程、异步节点线程，
 *       写入必须并发安全，读取时才排序成树。</li>
 * </ul>
 */
@Component
public class SpanRecorder {

    /** 一段调用记录（对外只读快照）。 */
    public record Span(String traceId,
                       String spanId,
                       String parentId,
                       String layer,
                       String name,
                       String startedAt,
                       long durationMs,
                       String outcome,
                       String failureType,
                       Map<String, String> attributes) {

        public Span {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    /** 树上的一个节点（诊断接口的输出形状）。 */
    public record TraceNode(String spanId,
                            String layer,
                            String name,
                            String startedAt,
                            long durationMs,
                            String outcome,
                            String failureType,
                            Map<String, String> attributes,
                            List<TraceNode> children) {
    }

    /** 已开始、未结束的片段句柄。 */
    public static final class SpanHandle {

        private final String spanId;

        private SpanHandle(String spanId) {
            this.spanId = spanId;
        }

        public String spanId() {
            return spanId;
        }
    }

    /** 内部可变片段：开始时有身份，结束时补结果与耗时。 */
    private static final class MutableSpan {

        private final String traceId;
        private final String spanId;
        private final String parentId;
        private final String layer;
        private final String name;
        private final String startedAt;
        private final Map<String, String> attributes;
        private volatile long durationMs;
        private volatile String outcome;
        private volatile String failureType = "";

        private MutableSpan(String traceId, String spanId, String parentId, String layer, String name,
                            String startedAt, Map<String, String> attributes) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentId = parentId;
            this.layer = layer;
            this.name = name;
            this.startedAt = startedAt;
            this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        private Span snapshot() {
            return new Span(traceId, spanId, parentId, layer, name, startedAt, durationMs,
                    outcome == null ? "running" : outcome, failureType, attributes);
        }
    }

    private static final int DEFAULT_MAX_TRACES = 500;

    private final Map<String, List<MutableSpan>> spansByTrace = new ConcurrentHashMap<>();
    private final Map<String, Long> order = new ConcurrentHashMap<>();
    private final Map<String, MutableSpan> spansById = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final int maxTraces;

    public SpanRecorder() {
        this(DEFAULT_MAX_TRACES);
    }

    public SpanRecorder(int maxTraces) {
        this.maxTraces = maxTraces;
    }

    /** 开始一段调用：立刻进缓冲区，子片段才有父节点可挂。 */
    public SpanHandle startSpan(String traceId, String parentId, String layer, String name,
                                Instant startedAt, Map<String, String> attributes) {
        String effectiveTrace = traceId == null || traceId.isBlank() ? "unknown" : traceId;
        String spanId = effectiveTrace + "-" + sequence.incrementAndGet();
        MutableSpan span = new MutableSpan(effectiveTrace, spanId, parentId, layer, name,
                startedAt == null ? Instant.now().toString() : startedAt.toString(), attributes);
        evictIfNeeded(effectiveTrace);
        spansByTrace.computeIfAbsent(effectiveTrace, key -> new CopyOnWriteArrayList<>()).add(span);
        spansById.put(spanId, span);
        return new SpanHandle(spanId);
    }

    /** 结束一段调用：补上结果、失败分类与耗时。 */
    public void finish(SpanHandle handle, String outcome, FailureType failureType, long durationMs) {
        if (handle == null) {
            return;
        }
        MutableSpan span = spansById.get(handle.spanId());
        if (span == null) {
            return;
        }
        span.durationMs = Math.max(0, durationMs);
        span.outcome = outcome == null ? "ok" : outcome;
        span.failureType = failureType == null ? "" : failureType.name();
    }

    /** 一次性记录一段已完成的调用（测试与简单场景用）。 */
    public Span record(String traceId, String parentId, String layer, String name, Instant startedAt,
                       long durationMs, String outcome, FailureType failureType,
                       Map<String, String> attributes) {
        SpanHandle handle = startSpan(traceId, parentId, layer, name, startedAt, attributes);
        finish(handle, outcome, failureType, durationMs);
        return spansById.get(handle.spanId()).snapshot();
    }

    public List<Span> spansOf(String traceId) {
        List<MutableSpan> spans = spansByTrace.get(traceId);
        if (spans == null) {
            return List.of();
        }
        return spans.stream().map(MutableSpan::snapshot).toList();
    }

    public List<String> recentTraceIds(int limit) {
        return order.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public int trackedTraceCount() {
        return spansByTrace.size();
    }

    /** 把扁平片段还原成调用树：没有父节点的片段作为根（通常就是 HTTP 入口那段）。 */
    public List<TraceNode> treeOf(String traceId) {
        List<Span> spans = spansOf(traceId);
        if (spans.isEmpty()) {
            return List.of();
        }
        Map<String, List<Span>> childrenByParent = new LinkedHashMap<>();
        List<Span> roots = new ArrayList<>();
        for (Span span : spans) {
            String parentId = span.parentId();
            boolean hasParent = parentId != null && !parentId.isBlank()
                    && spans.stream().anyMatch(candidate -> candidate.spanId().equals(parentId));
            if (hasParent) {
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(span);
            } else {
                roots.add(span);
            }
        }
        // 按开始时间排序：同一层的先后顺序本身就是排查线索
        Comparator<Span> byStart = Comparator.comparing(Span::startedAt);
        return roots.stream().sorted(byStart).map(root -> toNode(root, childrenByParent, byStart)).toList();
    }

    private static TraceNode toNode(Span span, Map<String, List<Span>> childrenByParent, Comparator<Span> byStart) {
        List<Span> children = childrenByParent.getOrDefault(span.spanId(), List.of());
        return new TraceNode(span.spanId(), span.layer(), span.name(), span.startedAt(),
                span.durationMs(), span.outcome(), span.failureType(), span.attributes(),
                children.stream().sorted(byStart).map(child -> toNode(child, childrenByParent, byStart)).toList());
    }

    /**
     * 容量控制：**先腾位置，再写**。
     *
     * <p>第一版写成「先写、再顺手淘汰」，结果容量上限被突破 1（实测 5 次写入、上限 2 却留着 3 个 trace）——
     * 因为淘汰时最新的那个 trace 不能被淘汰，只能提前返回。缓冲区的上限一旦是「大约」，
     * 它在长跑进程里就只是延缓了内存增长，不是封顶。所以规则改为：写入前把 size 压到 {@code maxTraces - 1}。
     */
    private void evictIfNeeded(String traceId) {
        order.putIfAbsent(traceId, System.nanoTime());
        while (spansByTrace.size() >= maxTraces) {
            String oldest = order.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(traceId))
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) {
                // 只剩当前这个 trace：它即将被写入，不能再淘汰，否则会出现「写进去又立刻消失」
                return;
            }
            List<MutableSpan> evicted = spansByTrace.remove(oldest);
            order.remove(oldest);
            if (evicted != null) {
                evicted.forEach(span -> spansById.remove(span.spanId));
            }
        }
    }
}
