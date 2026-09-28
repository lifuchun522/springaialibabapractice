package com.example.digitalhuman.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 第 17 掌：调用树能不能还原，全靠这份记录。
 *
 * <p>要证明的只有一件事：**给一个 traceId，能拿到一棵带层与失败的树**。
 * 在这之前，同事能拿到的只有「一屏互不相认的日志」。
 */
class SpanRecorderTest {

    private static final String TRACE = "trace-tree";

    private SpanRecorder.Span record(SpanRecorder recorder, String parentId, String layer,
                                     String name, String outcome, FailureType failureType) {
        return recorder.record(TRACE, parentId, layer, name, Instant.now(), 12L, outcome, failureType,
                Map.of("layer", layer));
    }

    @Test
    @DisplayName("父子关系还原成树：一次失败请求能看出它死在哪一层")
    void shouldRebuildCallTree() {
        SpanRecorder recorder = new SpanRecorder(10);

        SpanRecorder.Span http = record(recorder, null, "http", "POST /api/projects/{id}/chat", "ok", null);
        SpanRecorder.Span model = record(recorder, http.spanId(), "model", "genai.chat", "failed",
                FailureType.MODEL_ERROR);
        record(recorder, http.spanId(), "rag", "genai.rag.answer", "rejected", FailureType.RAG_EMPTY);

        List<SpanRecorder.TraceNode> tree = recorder.treeOf(TRACE);

        assertThat(tree).hasSize(1);
        SpanRecorder.TraceNode root = tree.get(0);
        assertThat(root.layer()).isEqualTo("http");
        assertThat(root.children()).hasSize(2);
        assertThat(root.children()).extracting(SpanRecorder.TraceNode::failureType)
                .containsExactlyInAnyOrder("MODEL_ERROR", "RAG_EMPTY");
        assertThat(root.children()).extracting(SpanRecorder.TraceNode::layer)
                .containsExactlyInAnyOrder("model", "rag");
        assertThat(model.failureType()).isEqualTo(FailureType.MODEL_ERROR.name());
    }

    @Test
    @DisplayName("没有父节点的片段当根：链路中途接入也能看见，不会凭空消失")
    void orphanSpansBecomeRoots() {
        SpanRecorder recorder = new SpanRecorder(10);

        record(recorder, "trace-tree-999", "tool", "genai.tool", "ok", null);

        List<SpanRecorder.TraceNode> tree = recorder.treeOf(TRACE);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).layer()).isEqualTo("tool");
    }

    @Test
    @DisplayName("容量有上限：诊断缓冲不能变成内存泄漏，且整棵树一起淘汰")
    void shouldEvictWholeTracesBeyondCapacity() {
        SpanRecorder recorder = new SpanRecorder(2);

        for (int index = 0; index < 5; index++) {
            recorder.record("trace-" + index, null, "http", "span", Instant.now(), 1L, "ok", null, Map.of());
        }

        assertThat(recorder.trackedTraceCount()).isLessThanOrEqualTo(2);
        assertThat(recorder.spansOf("trace-4")).as("最近的必须还在").isNotEmpty();
        assertThat(recorder.spansOf("trace-0")).as("最早的整棵被淘汰").isEmpty();
    }

    @Test
    @DisplayName("最近 traceId 可查：出事时不用先去翻日志找号")
    void shouldExposeRecentTraceIds() {
        SpanRecorder recorder = new SpanRecorder(10);

        record(recorder, null, "http", "span", "ok", null);

        assertThat(recorder.recentTraceIds(5)).contains(TRACE);
    }
}
