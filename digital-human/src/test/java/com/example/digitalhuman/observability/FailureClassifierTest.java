package com.example.digitalhuman.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.ai.ModelRoutingException;
import com.example.digitalhuman.rag.KnowledgeContractException;
import com.example.digitalhuman.service.ConversationBusyException;
import com.example.digitalhuman.service.ResourceNotFoundException;

/**
 * 第 17 掌：失败分类表要有出处，因为它决定排查方向。
 *
 * <p>分类错了比不分类更坏：它会把「模型超时」和「输入非法」聚合进同一个桶，
 * 于是告警响起来时没人知道该重试还是该改输入。所以这张表逐项被测过。
 */
class FailureClassifierTest {

    private static ModelInvocationException modelException(ModelInvocationException.Kind kind) {
        return new ModelInvocationException(kind, "deepseek", "deepseek-flash", "模拟失败", null);
    }

    @Test
    @DisplayName("模型侧：超时/不可达与鉴权/空响应要分成两类（一个可重试，一个多半不可）")
    void shouldSplitModelFailuresByRetryability() {
        assertThat(FailureClassifier.classify(modelException(ModelInvocationException.Kind.TIMEOUT)))
                .isEqualTo(FailureType.MODEL_TIMEOUT);
        assertThat(FailureClassifier.classify(modelException(ModelInvocationException.Kind.UNAVAILABLE)))
                .isEqualTo(FailureType.MODEL_TIMEOUT);
        assertThat(FailureClassifier.classify(modelException(ModelInvocationException.Kind.AUTH)))
                .isEqualTo(FailureType.MODEL_ERROR);
        assertThat(FailureClassifier.classify(modelException(ModelInvocationException.Kind.EMPTY_RESPONSE)))
                .as("「200 但没有内容」是最容易伪装成成功的一种失败")
                .isEqualTo(FailureType.MODEL_ERROR);
    }

    @Test
    @DisplayName("入参与会话类：调用方问题不重试，会话忙可重试但要退避")
    void shouldClassifyCallerSideFailures() {
        assertThat(FailureClassifier.classify(new IllegalArgumentException("text 不能为空")))
                .isEqualTo(FailureType.INPUT_INVALID);
        assertThat(FailureClassifier.classify(new ModelRoutingException("暂不支持的 provider")))
                .isEqualTo(FailureType.INPUT_INVALID);
        assertThat(FailureClassifier.classify(new ResourceNotFoundException("项目不存在")))
                .isEqualTo(FailureType.INPUT_INVALID);
        assertThat(FailureClassifier.classify(new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数错")))
                .isEqualTo(FailureType.INPUT_INVALID);
        assertThat(FailureClassifier.classify(new ConversationBusyException("s-1")))
                .isEqualTo(FailureType.CONVERSATION_BUSY);
    }

    @Test
    @DisplayName("基础设施类：RAG 契约、远程超时各归其类")
    void shouldClassifyInfrastructureFailures() {
        assertThat(FailureClassifier.classify(new KnowledgeContractException("写入后回读校验失败")))
                .isEqualTo(FailureType.RAG_ERROR);
        assertThat(FailureClassifier.classify(new TimeoutException("远程 Agent 超时")))
                .isEqualTo(FailureType.REMOTE_AGENT_ERROR);
        assertThat(FailureClassifier.classify(new IllegalStateException("说不清是什么")))
                .as("分不出来就 UNKNOWN：它变多说明分类表该补了")
                .isEqualTo(FailureType.UNKNOWN);
    }

    @Test
    @DisplayName("同一异常在不同层含义不同：工具里的超时是 TOOL_ERROR")
    void layerRefinesTheClassification() {
        assertThat(FailureClassifier.classifyForLayer("tool", new TimeoutException("工具慢")))
                .isEqualTo(FailureType.TOOL_ERROR);
        assertThat(FailureClassifier.classifyForLayer("a2a", new IllegalStateException("远端说不清")))
                .isEqualTo(FailureType.REMOTE_AGENT_ERROR);
        assertThat(FailureClassifier.classifyForLayer("graph", new IllegalStateException("节点炸了")))
                .isEqualTo(FailureType.GRAPH_NODE_ERROR);
        assertThat(FailureClassifier.classifyForLayer("rag", new IllegalStateException("向量库抽风")))
                .isEqualTo(FailureType.RAG_ERROR);
        // 模型层的语义不该被 layer 覆盖：鉴权失败就是 MODEL_ERROR
        assertThat(FailureClassifier.classifyForLayer("model",
                modelException(ModelInvocationException.Kind.AUTH)))
                .isEqualTo(FailureType.MODEL_ERROR);
    }

    @Test
    @DisplayName("包装异常要剥到底：远程工具的异常常常套了两层")
    void shouldUnwrapWrappedFailures() {
        Throwable wrapped = new java.util.concurrent.CompletionException(
                new java.util.concurrent.ExecutionException(new TimeoutException("下游超时")));

        assertThat(FailureClassifier.classifyForLayer("tool", wrapped)).isEqualTo(FailureType.TOOL_ERROR);
    }

    @Test
    @DisplayName("HTTP 层按状态码分类：异常被 @ExceptionHandler 吃掉时，状态码是唯一可靠的事实")
    void shouldClassifyByHttpStatus() {
        assertThat(FailureClassifier.classifyHttpStatus(200)).isNull();
        assertThat(FailureClassifier.classifyHttpStatus(400)).isEqualTo(FailureType.INPUT_INVALID);
        assertThat(FailureClassifier.classifyHttpStatus(404)).isEqualTo(FailureType.INPUT_INVALID);
        assertThat(FailureClassifier.classifyHttpStatus(409)).isEqualTo(FailureType.CONVERSATION_BUSY);
        assertThat(FailureClassifier.classifyHttpStatus(429)).isEqualTo(FailureType.CONVERSATION_BUSY);
        assertThat(FailureClassifier.classifyHttpStatus(500))
                .as("5xx 不在这里猜层：具体哪一层坏的，由更内层的片段给出")
                .isEqualTo(FailureType.UNKNOWN);
    }

    @Test
    @DisplayName("调用树记录：一段失败要在树里看得见层、耗时与分类")
    void observationShouldRecordSpansWithFailureType() {
        SpanRecorder recorder = new SpanRecorder(10);
        ObservedOperation observed = TestObservability.withRegistry(io.micrometer.observation.ObservationRegistry.create());

        RequestContext.bind(new RequestContext("trace-x", "1", "s-1", "1:s-1"));
        try {
            observed.observe("genai.chat", "model", Map.of("model", "deepseek-flash"), () -> "ok");

            assertThat(recorder).isNotNull();
            // 工具超时在真实链路里也是被包一层再抛出来的（Future.get 的包装），这里照同样的形状构造
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> observed.observe("genai.tool", "tool",
                            Map.of("tool.name", "getProjectInfo"),
                            () -> {
                                throw new java.util.concurrent.CompletionException(new TimeoutException("工具超时"));
                            }))
                    .isInstanceOf(java.util.concurrent.CompletionException.class);
        } finally {
            RequestContext.clear();
        }
    }
}
