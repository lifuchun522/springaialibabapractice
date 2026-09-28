package com.example.digitalhuman.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次编排运行的节点时间线：**经过哪些节点、每个节点的时间跨度、吐了多少次输出**。
 *
 * <p>为什么这是编排的第一等产出：第 10 掌的验收里有一条是「每次响应都能打印出经过的节点名与各自耗时」。
 * 没有节点就没有埋点——失败点藏在自由文本的推理里时，想查也无处下手。
 *
 * <p>与第 9 掌的 {@code AgentTrace} 的区别：那个是「一次 ReAct 循环里模型/工具各调了几次」，
 * 这个是「一条编排链里经过了哪些节点、每个节点多久」。两者都要有：
 * 循环再稳也不代表流程可复现，流程可复现也不代表单次调用没有失控。
 *
 * <p><b>为什么按节点名聚合，而不是一条输出一行</b>（实测踩出来的）：
 * 框架是**流式**吐节点输出的——一个节点会产生很多条输出，并行分支还会逐条交错。
 * 一次简单调用能产生上百条 NodeOutput，直接按输出记录，行数就是噪声，
 * 而且并行的两条分支会交替出现，看的人根本判断不出「谁跑了多久」。
 * 所以这里按节点名聚合：耗时取该节点**首次出现到末次出现的时间跨度**，
 * 并额外给出输出条数。并行分支的时间跨度互相重叠，恰好说明它们真的在并行。
 *
 * <p>线程安全：并行分支会在不同线程上同时上报。
 */
public final class WorkflowTrace {

    private final String traceId;
    private final String mode;
    private final Map<String, MutableTiming> byNode = new LinkedHashMap<>();
    private final List<String> sequence = new ArrayList<>();

    public WorkflowTrace(String traceId, String mode) {
        this.traceId = traceId;
        this.mode = mode;
    }

    public String traceId() {
        return traceId;
    }

    public String mode() {
        return mode;
    }

    /** 观察到一个节点的输出：atMs 是相对本次编排开始的毫秒数。 */
    public synchronized void observe(String name, long atMs) {
        MutableTiming timing = byNode.get(name);
        if (timing == null) {
            byNode.put(name, new MutableTiming(atMs, atMs, 1));
        } else {
            timing.lastSeenAt = atMs;
            timing.emissions++;
        }
        // 序列视图：只记「换了一个节点」这件事，连续同一个节点不重复记。
        // 为什么两个视图都要有：聚合视图回答「各花了多久」，序列视图回答「顺序是否可复现、循环跑了几轮」
        if (sequence.isEmpty() || !sequence.get(sequence.size() - 1).equals(name)) {
            sequence.add(name);
        }
    }

    /** 记一个「代码节点」（不是框架里的 Agent，例如归并）：一次、耗时直接给出。 */
    public synchronized void node(String name, long elapsedMs) {
        byNode.put(name, new MutableTiming(elapsedMs, elapsedMs, 1));
        if (sequence.isEmpty() || !sequence.get(sequence.size() - 1).equals(name)) {
            sequence.add(name);
        }
    }

    /**
     * 节点顺序序列（连续同一个节点只记一次）。
     *
     * <p>验收 1 用它：同一问题二十次，这个序列必须完全一致。
     * 循环用它：同一个节点出现两次 = 循环体跑了两轮。
     * 并行模式下两条分支会交替进来，所以并行时它读作「交错顺序」，不是「先后顺序」。
     */
    public synchronized List<String> sequence() {
        return List.copyOf(sequence);
    }

    public synchronized List<NodeTiming> nodes() {
        List<NodeTiming> rows = new ArrayList<>(byNode.size());
        byNode.forEach((name, timing) -> rows.add(
                new NodeTiming(name, timing.lastSeenAt - timing.firstSeenAt, timing.emissions)));
        return List.copyOf(rows);
    }

    /** 节点名序列（按首次出现顺序）：验收 1 只关心「顺序是否恒定」。 */
    public synchronized List<String> nodeNames() {
        return List.copyOf(byNode.keySet());
    }

    /**
     * @param elapsedMs 该节点首次出现到末次出现的时间跨度；并行分支之间会重叠
     * @param emissions 该节点吐出的输出条数（流式下一个节点会有很多条）
     */
    public record NodeTiming(String node, long elapsedMs, int emissions) {
    }

    private static final class MutableTiming {
        private final long firstSeenAt;
        private long lastSeenAt;
        private int emissions;

        private MutableTiming(long firstSeenAt, long lastSeenAt, int emissions) {
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = lastSeenAt;
            this.emissions = emissions;
        }
    }
}
