package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 9 掌四条验收中，能在离线环境测的部分：
 * <ol>
 *   <li>事件序列完整可看：agent.start → model#1 → tool:xxx → model#2 → agent.end；</li>
 *   <li>单次请求模型调用有硬上界，超界显式结束（不是超时）；</li>
 *   <li>调用方拿到的仍然是一个字符串。</li>
 * </ol>
 *
 * <p><b>一个值得记下的能力变化</b>：第 6 掌时「模型发起工具调用」这条链是测不出来的——
 * 那时循环在真实 ChatModel 实现内部（OpenAiChatModel 持有 ToolCallingManager），
 * 假模型根本不会执行工具。换成 Agent Framework 后，循环搬到了框架的图节点里，
 * 于是用假模型也能把「模型写 tool_calls → 框架执行工具 → 结果回灌 → 再问模型」整条链跑通。
 * 也就是说：**这一掌不只换来了可观测性，还换来了可测试性。**
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentFlowTest {

    private static final String SESSION = "agent-s1";

    /** 与 digital-human.agent.model-call-limit 对齐；改配置必须同步改这里，否则上界测试会失效。 */
    private static final int LIMIT = 8;

    /** 与 digital-human.tools.max-retries 对齐；改配置必须同步改这里。 */
    private static final int RETRIES = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ChatLedgerService ledgerService;

    @Autowired
    private AuthService authService;

    @Autowired
    private com.example.digitalhuman.repository.ToolCallAuditRepository audits;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;

    @BeforeAll
    void prepareProjectWithHistory() {
        authService.register("agent-owner", "password123");
        AuthService.AuthToken auth = authService.login("agent-owner", "password123");

        DigitalHumanProject project = projectService.create(auth.userId(), new ProjectService.ProjectCommand(
                "Agent 演示", "数字人小 A", "#2F6BFF", null, "你好", "再见", null, "你是数字人小 A。"));
        projectId = project.getId();

        // 造 3 条真实账本记录：session_stats 工具会读它
        ConversationId conversationId = ConversationId.of(auth.userId(), projectId, SESSION);
        ledgerService.append(projectId, conversationId, auth.userId(), ChatMessage.Role.USER,
                "第一句", ChatMessage.Status.COMPLETED);
        ledgerService.append(projectId, conversationId, auth.userId(), ChatMessage.Role.ASSISTANT,
                "第二句", ChatMessage.Status.COMPLETED);
        ledgerService.append(projectId, conversationId, auth.userId(), ChatMessage.Role.USER,
                "第三句", ChatMessage.Status.COMPLETED);
    }

    @BeforeEach
    void resetModel() {
        org.mockito.Mockito.reset(chatModel);
    }

    private static ChatResponse toolCallResponse(String toolName, String argumentsJson) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", toolName, argumentsJson)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private MvcResult call(String question) throws Exception {
        return mockMvc.perform(post("/api/projects/" + projectId + "/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("question", question, "sessionId", SESSION))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static List<String> eventsOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(body).get("events").findValuesAsText("");
    }

    @Test
    @DisplayName("agent_shouldExposeFullNodeEventSequence")
    void agent_shouldExposeFullNodeEventSequence() throws Exception {
        // 第一个响应是工具调用（模型只写参数），第二个响应才是最终回答
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("session_stats", "{\"metric\":\"MESSAGE_COUNT\"}"),
                        textResponse("这个会话一共有 3 条消息。"));

        MvcResult result = call("这个会话有多少条消息？");

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var json = objectMapper.readTree(body);

        // 验收 1：事件序列完整可看
        List<String> events = new java.util.ArrayList<>();
        json.get("events").forEach(node -> events.add(node.asText()));
        assertThat(events).contains("agent.start", "model#1", "tool:session_stats", "model#2", "agent.end");

        // 验收 4：调用方拿到的仍然是一个字符串
        assertThat(json.get("reply").asText()).isEqualTo("这个会话一共有 3 条消息。");
        assertThat(json.get("modelCalls").asInt()).isEqualTo(2);
        assertThat(json.get("traceId").asText()).hasSize(12);
    }

    @Test
    @DisplayName("agent_shouldStopAtModelCallLimitInsteadOfLoopingForever")
    void agent_shouldStopAtModelCallLimitInsteadOfLoopingForever() throws Exception {
        // 模型每次都返回同一个工具调用：没有上界的话它就会一直转
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("session_stats", "{\"metric\":\"MESSAGE_COUNT\"}"));

        MvcResult result = call("一直查会话条数");

        var json = objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        // 验收 2：硬上界生效且是「显式结束」，不是超时；调用方仍拿到一个字符串
        assertThat(json.get("modelCalls").asInt()).isEqualTo(LIMIT);
        assertThat(json.get("reply").asText()).isNotBlank();

        List<String> events = new java.util.ArrayList<>();
        json.get("events").forEach(node -> events.add(node.asText()));
        // 以 agent.end 收尾（而不是 agent.error）：这是「按预算结束」，不是跑挂了
        assertThat(events).startsWith("agent.start").endsWith("agent.end");
        assertThat(events).doesNotContain("agent.error");
        assertThat(events).contains("model#1", "model#" + LIMIT);

        // 最关键的一条：上界不只在「我们自己的计数器」里成立，模型这一侧也真的只被调了 8 次。
        // 注意不能直接数 getInvocations().size()——框架构建/调用期还会调 getDefaultOptions 之类的
        // 非推理方法（实测 10 次），把它算进来就会得出「调了 18 次模型」的假结论。
        long providerCalls = org.mockito.Mockito.mockingDetails(chatModel).getInvocations().stream()
                .filter(invocation -> "call".equals(invocation.getMethod().getName()))
                .count();
        assertThat(providerCalls).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("agent_shouldReturnStringEvenWhenModelProducesNothing")
    void agent_shouldReturnStringEvenWhenModelProducesNothing() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("   "));

        MvcResult result = call("随便问一句");

        var json = objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        // 空内容也要变成可展示的一句话，而不是 null
        assertThat(json.get("reply").asText()).isNotBlank();
        assertThat(json.get("events").toString()).contains("agent.end");
    }

    @Test
    @DisplayName("agent_shouldRetryFailingToolAndStillAnswerUser")
    void agent_shouldRetryFailingToolAndStillAnswerUser() throws Exception {
        int errorBefore = errorAuditCount();

        // 模型填了一个 Schema 白名单外的参数值：工具必然失败，且失败发生在工具调用内部
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("session_stats", "{\"metric\":\"NOT_A_METRIC\"}"),
                        textResponse("我暂时查不到会话条数，先按已有信息回答你。"));

        MvcResult result = call("这个会话有多少条消息？");

        var json = objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        // 验收 3：失败被重试（1 次原始调用 + max-retries 次重试），重试耗尽后 Agent 仍产出回答
        assertThat(json.get("reply").asText()).isEqualTo("我暂时查不到会话条数，先按已有信息回答你。");
        assertThat(errorAuditCount() - errorBefore).isEqualTo(RETRIES + 1);

        // 重试发生在工具边界内，事件序列里看到的仍然是「一次工具调用」，收尾正常
        List<String> events = new java.util.ArrayList<>();
        json.get("events").forEach(node -> events.add(node.asText()));
        assertThat(events).contains("tool:session_stats", "agent.end").doesNotContain("agent.error");
    }

    /** 审计表里失败的工具调用条数：重试了几次不靠日志猜，靠这张表数。 */
    private int errorAuditCount() {
        return (int) audits.findByProjectIdOrderByIdDesc(projectId).stream()
                .filter(audit -> audit.getStatus() == ToolCallAudit.Status.ERROR)
                .count();
    }

    @Test
    @DisplayName("agent_shouldRejectBlankQuestion")
    void agent_shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
