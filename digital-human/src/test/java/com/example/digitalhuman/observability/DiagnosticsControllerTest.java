package com.example.digitalhuman.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 第 17 掌的门面验收：**给一个 traceId，能不能还原出这次请求的调用树**。
 *
 * <p>这份用例把本掌的四条完成标准里最硬的两条钉住了：
 * <ol>
 *   <li>traceId 能贯穿到最内层（这里最内层是模型调用）；</li>
 *   <li>诊断面能把它还原成树，并且**不回正文**——用户输入与模型输出都不该出现在诊断响应里。</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=observability-test-key-not-a-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiagnosticsControllerTest {

    private static final String SECRET_QUESTION = "请告诉我内部成本价";

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpanRecorder recorder;

    @Autowired
    private com.example.digitalhuman.service.AuthService authService;

    private String tokenOf(String username) {
        // 复用真实的注册/登录链路拿令牌，避免为了测试绕开鉴权
        try {
            mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                            .contentType("application/json")
                            .content("{\"username\":\"" + username + "\",\"password\":\"pass-123456\"}"))
                    .andReturn();
            String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + username + "\",\"password\":\"pass-123456\"}"))
                    .andReturn().getResponse().getContentAsString();
            int index = body.indexOf("token\":\"");
            return body.substring(index + 8, body.indexOf('"', index + 8));
        } catch (Exception ex) {
            throw new IllegalStateException("测试内登录失败", ex);
        }
    }

    @Test
    @DisplayName("一次请求：响应头带回 traceId，日志身份四元组齐备，且调用树可按 traceId 还原")
    void shouldRecordTraceAndServeTree() throws Exception {
        given(chatModel.call(any(Prompt.class))).willReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("你好")))));

        // 注意：这里**不**断言「继承调用方传入的号」。跨进程续链用的是 W3C traceparent，
        // 由框架在真实 servlet 栈里完成接续（MockMvc 的过滤器顺序与真实容器不同，
        // 在这里断言它只会得到一个关于测试脚手架的结论）。这里断言的是应用内部一致：
        // 响应头带回的号 == 调用树记录的号 == 诊断接口查得到的号。
        var response = mockMvc.perform(MockMvcRequestBuilders.get("/api/chat")
                        .param("q", SECRET_QUESTION)
                        .header(RequestContext.HEADER_PROJECT_ID, "1")
                        .header(RequestContext.HEADER_SESSION_ID, "s-1"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        String incomingTrace = response.getHeader(RequestContext.HEADER_TRACE_ID);
        assertThat(incomingTrace)
                .as("响应头要带回 traceId：用户截图投诉时能直接拿号去查")
                .matches("[0-9a-f]{32}");

        // 调用树：至少要有模型那一段，并且落在同一个 traceId 上
        List<SpanRecorder.Span> spans = recorder.spansOf(incomingTrace);
        assertThat(spans).isNotEmpty();
        assertThat(spans).extracting(SpanRecorder.Span::layer).contains("model");
        assertThat(recorder.treeOf(incomingTrace)).isNotEmpty();

        // 诊断面：登录后可查，返回树结构
        String token = tokenOf("observer-" + System.nanoTime());
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/diagnostics/traces/{id}", incomingTrace)
                        .header("X-Token", token))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains(incomingTrace).contains("genai.chat").contains("model");
        assertThat(json)
                .as("诊断面只回结构与结果，不回正文：用户输入与模型输出都不该出现在这里")
                .doesNotContain(SECRET_QUESTION)
                .doesNotContain("你好");
    }

    @Test
    @DisplayName("调用树是树不是一堆片段：模型片段挂在 http 根节点下")
    void shouldNestSpansUnderHttpRoot() throws Exception {
        given(chatModel.call(any(Prompt.class))).willReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("好")))));

        var response = mockMvc.perform(MockMvcRequestBuilders.get("/api/chat")
                        .param("q", "你好").param("sessionId", "s-tree"))
                .andReturn().getResponse();
        String traceId = response.getHeader(RequestContext.HEADER_TRACE_ID);

        List<SpanRecorder.TraceNode> tree = recorder.treeOf(traceId);

        assertThat(tree).as("一次请求只该有一个根：HTTP 入口").hasSize(1);
        SpanRecorder.TraceNode root = tree.get(0);
        assertThat(root.layer()).isEqualTo("http");
        assertThat(root.children()).extracting(SpanRecorder.TraceNode::layer).contains("model");
        assertThat(root.children()).allSatisfy(child ->
                assertThat(child.spanId()).as("子片段必须有独立 id，树才连得起来").isNotEqualTo(root.spanId()));
    }

    @Test
    @DisplayName("失败得早也要有痕迹：项目不存在的请求同样留下一棵带分类的树")
    void earlyFailureStillLeavesATrace() throws Exception {
        // 这个请求在进入任何业务埋点之前就会失败（项目不存在）
        var response = mockMvc.perform(MockMvcRequestBuilders.post("/api/projects/{id}/chat", 999999)
                        .contentType("application/json")
                        .content("{\"text\":\"你好\"}"))
                .andReturn().getResponse();
        String traceId = response.getHeader(RequestContext.HEADER_TRACE_ID);

        assertThat(traceId).as("哪怕是失败的请求，也要能拿到号").isNotBlank();

        List<SpanRecorder.Span> spans = recorder.spansOf(traceId);
        assertThat(spans).as("早失败如果没有 HTTP 层埋点，诊断面就是一棵空树——那正是最需要答案的时候")
                .isNotEmpty();
        assertThat(spans).extracting(SpanRecorder.Span::failureType).contains("INPUT_INVALID");
        assertThat(spans).extracting(SpanRecorder.Span::layer).contains("http");
    }

    @Test
    @DisplayName("没有登录态时诊断面拒绝：内部结构与耗时分布不能匿名可读")
    void diagnosticsRequireLogin() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/diagnostics/traces/whatever"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("不带 traceId 的请求也会被分配一个：不是只有「配合的调用方」才有链路")
    void shouldAssignTraceIdWhenAbsent() throws Exception {
        given(chatModel.call(any(Prompt.class))).willReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("好")))));

        var response = mockMvc.perform(MockMvcRequestBuilders.get("/api/chat").param("q", "你好"))
                .andReturn().getResponse();

        assertThat(response.getHeader(RequestContext.HEADER_TRACE_ID)).isNotBlank();
        assertThat(recorder.spansOf(response.getHeader(RequestContext.HEADER_TRACE_ID))).isNotEmpty();
    }

    @Test
    @DisplayName("authService 可用（令牌链路是真的，不是绕过鉴权）")
    void authServiceIsWired() {
        assertThat(authService).isNotNull();
    }
}
