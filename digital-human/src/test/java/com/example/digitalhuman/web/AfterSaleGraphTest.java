package com.example.digitalhuman.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 11 掌的四条验收线里能在离线环境证明的部分。
 *
 * <p>这一掌最值得测的不是「跑通了」，而是**两个不会报错的坑**：
 * <ul>
 *   <li>归约策略配错：不抛异常、不打日志，只是数据没了（并行分支上尤其明显）；</li>
 *   <li>threadId 不一致：检查点找不到，表现就是「恢复时前面的节点又跑了一遍」。</li>
 * </ul>
 * 所以这里的断言盯的是「状态里到底留下了什么」和「第二次调用到底执行了哪些节点」。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AfterSaleGraphTest {

    private static final String HIGH_RISK_QUESTION = "我的订单 A20240617 想退款，已经签收了";
    private static final String LOW_RISK_QUESTION = "你们展厅周一开门吗？";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectService projectService;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;

    @BeforeAll
    void prepareProject() {
        authService.register("graph-owner", "password123");
        AuthService.AuthToken auth = authService.login("graph-owner", "password123");
        DigitalHumanProject project = projectService.create(auth.userId(), new ProjectService.ProjectCommand(
                "售后图演示", "数字人小图", "#2F6BFF", null, "你好", "再见", null, "你是数字人小图。"));
        projectId = project.getId();
    }

    @BeforeEach
    void resetModel() {
        org.mockito.Mockito.reset(chatModel);
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * 桩要同时给 call 与 stream。
     *
     * <p>分工（第 10 掌踩过一次，这里同样成立）：分类节点与答复节点走 {@code call}，
     * 而 {@code biz} 那一格是 {@code ReactAgent.asNode()}，它内部的 AgentLlmNode 走 {@code stream}。
     * 只桩一个就会得到「模型返回 null」的假故障。
     */
    private void stubIntent(String intentLabel) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse(intentLabel), textResponse("已受理，退款会在 3 个工作日内原路退回。[1]"));
        when(chatModel.stream(any(Prompt.class)))
                .thenAnswer(invocation -> reactor.core.publisher.Flux.just(
                        textResponse("业务记录：展厅预约余位 0，会话消息 3 条。")));
    }

    private MvcResult run(String question, String threadId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/after-sale/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", question, "sessionId", "graph-s1", "threadId", threadId))))
                .andExpect(status().isOk())
                .andReturn();
        result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        return result;
    }

    private MvcResult resume(String threadId, String decision) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/after-sale/" + threadId + "/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", decision, "sessionId", "graph-s1"))))
                .andExpect(status().isOk())
                .andReturn();
        result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        return result;
    }

    private static List<String> executedNodes(MvcResult result) throws Exception {
        var json = new ObjectMapper().readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> nodes = new ArrayList<>();
        json.get("executedNodes").forEach(node -> nodes.add(node.asText()));
        return nodes;
    }

    @Test
    @DisplayName("lowRisk_shouldRunThroughInOneShot")
    void lowRisk_shouldRunThroughInOneShot() throws Exception {
        stubIntent("VISIT");

        MvcResult result = run(LOW_RISK_QUESTION, "t-low-1");
        var json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(json.get("status").asText()).isEqualTo("DONE");
        // 低风险不进人工节点：条件边直接走 reply
        assertThat(executedNodes(result)).contains("intent", "risk", "reply").doesNotContain("humanReview");
        assertThat(json.get("state").get("riskLevel").asText()).isEqualTo("LOW");
        assertThat(json.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("highRisk_shouldInterruptBeforeHumanReviewInsteadOfBlocking")
    void highRisk_shouldInterruptBeforeHumanReviewInsteadOfBlocking() throws Exception {
        stubIntent("REFUND");

        MvcResult result = run(HIGH_RISK_QUESTION, "t-high-1");
        var json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 验收第二条：停下来，把控制权交回调用方——调用方没有阻塞线程
        assertThat(json.get("status").asText()).isEqualTo("INTERRUPTED");
        assertThat(json.get("state").get("riskLevel").asText()).isEqualTo("HIGH");
        // 停在人工节点**之前**：reply 还没跑，所以还没有答复
        assertThat(executedNodes(result)).contains("intent", "biz", "risk")
                .doesNotContain("humanReview", "reply");
        // knowledge 与 biz 在同一 super-step 并行：事件流里只看得见 Agent 那一格，
        // 所以「knowledge 到底跑没跑」要看节点自报日志（写在状态里，跟着检查点一起落盘）
        assertThat(json.get("state").get("nodeLog").asText())
                .contains("intent").contains("knowledge").contains("risk");
        assertThat(json.get("state").get("knowledgeHits").asText()).isNotBlank();
        assertThat(json.get("reply").asText()).isEmpty();
    }

    @Test
    @DisplayName("resume_shouldContinueFromCheckpointWithoutRerunningEarlierNodes")
    void resume_shouldContinueFromCheckpointWithoutRerunningEarlierNodes() throws Exception {
        stubIntent("REFUND");
        run(HIGH_RISK_QUESTION, "t-resume-1");

        // 人工批准后继续：同一个 threadId
        MvcResult resumed = resume("t-resume-1", "APPROVE");
        List<String> nodes = executedNodes(resumed);
        var json = objectMapper.readTree(resumed.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(json.get("status").asText()).isEqualTo("DONE");
        // 验收第三条：从断点继续，前面的节点**不重跑**——这一次只执行了断点之后的节点
        assertThat(nodes).containsExactly("humanReview", "reply");
        assertThat(json.get("state").get("humanDecision").asText()).isEqualTo("APPROVE");
        assertThat(json.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("resume_shouldRejectWhenHumanSaysNo")
    void resume_shouldRejectWhenHumanSaysNo() throws Exception {
        stubIntent("REPAIR");
        run("我这台 X1 送修大概要多久？", "t-reject-1");

        MvcResult resumed = resume("t-reject-1", "REJECT");
        // 人工拒绝走确定性的兜底话术，不再让模型自由生成（不可逆动作的答复不该有采样空间）
        assertThat(objectMapper.readTree(
                resumed.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("reply").asText())
                .contains("人工");
    }

    @Test
    @DisplayName("checkpoints_shouldBeQueryableByThreadIdSoWeCanAnswerWhereItIsStuck")
    void checkpoints_shouldBeQueryableByThreadIdSoWeCanAnswerWhereItIsStuck() throws Exception {
        stubIntent("REFUND");
        run(HIGH_RISK_QUESTION, "t-state-1");

        var json = objectMapper.readTree(mockMvc.perform(
                        get("/api/projects/" + projectId + "/after-sale/t-state-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 验收第四条的一半：断点状态落了盘，能按 threadId 查回来
        assertThat(json.get("checkpointCount").asInt()).isPositive();
        var checkpoints = json.get("checkpoints");
        var last = checkpoints.get(checkpoints.size() - 1);
        // 「卡在哪一步」就是最后一条检查点的 nextNodeId
        assertThat(last.get("nextNodeId").asText()).isEqualTo("humanReview");
    }

    @Test
    @DisplayName("graph_shouldBeExportableAsDiagramWithoutReadingCode")
    void graph_shouldBeExportableAsDiagramWithoutReadingCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects/" + projectId + "/after-sale/graph"))
                .andExpect(status().isOk())
                .andReturn();
        result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());

        String mermaid = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("mermaid").asText();

        // 验收第一条：节点是节点、边是边，不读代码也能看懂流程
        assertThat(mermaid).contains("intent", "knowledge", "biz", "risk", "humanReview", "reply");
        assertThat(mermaid).contains("-->");   // 边确实存在
    }
}
