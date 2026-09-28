package com.example.digitalhuman.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 10 掌四条验收标准里能在离线环境证明的部分。
 *
 * <p>编排层最容易被误认为「测不了」——毕竟里面有 LLM。其实恰恰相反：
 * **编排把控制流从采样里拿出来了，所以控制流本身可以离线断言。**
 * 「同一问题二十次节点序列是否一致」这个问题，在单体 ReactAgent 上根本没法用假模型回答
 * （假模型不执行工具循环），在顺序编排上却是一个纯粹的确定性断言。
 *
 * <p>真实模型只负责它该负责的那部分：路由的语义判断准不准（见 docs/ch10-验收记录.md 的真实验收）。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowFlowTest {

    private static final String SESSION = "flows-1";
    /** 与 digital-human.workflow.loop-max-rounds 对齐。 */
    private static final int LOOP_ROUNDS = 2;

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
    private com.example.digitalhuman.agent.ProjectScopedTools projectScopedTools;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;

    @BeforeAll
    void prepareProject() {
        authService.register("flow-owner", "password123");
        AuthService.AuthToken auth = authService.login("flow-owner", "password123");
        DigitalHumanProject project = projectService.create(auth.userId(), new ProjectService.ProjectCommand(
                "编排演示", "数字人小流", "#2F6BFF", null, "你好", "再见", null, "你是数字人小流。"));
        projectId = project.getId();

        ConversationId conversationId = ConversationId.of(auth.userId(), projectId, SESSION);
        ledgerService.append(projectId, conversationId, auth.userId(), ChatMessage.Role.USER,
                "第一句", ChatMessage.Status.COMPLETED);
        ledgerService.append(projectId, conversationId, auth.userId(), ChatMessage.Role.ASSISTANT,
                "第二句", ChatMessage.Status.COMPLETED);
    }

    @BeforeEach
    void resetModel() {
        org.mockito.Mockito.reset(chatModel);
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * 编排节点走的是 {@code ChatModel.stream}（实测：AgentLlmNode 内部用 stream + map），
     * 路由节点走的是 {@code call}。所以两处都要桩上，
     * 否则会得到一个「模型返回 null」的假故障——这条是写测试时才发现的。
     */
    private void stubModel(String... responses) {
        ChatResponse[] chatResponses = java.util.Arrays.stream(responses)
                .map(WorkflowFlowTest::textResponse)
                .toArray(ChatResponse[]::new);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponses[0],
                java.util.Arrays.copyOfRange(chatResponses, 1, chatResponses.length));
        final String first = responses[0];
        when(chatModel.stream(any(Prompt.class)))
                .thenAnswer(invocation -> reactor.core.publisher.Flux.just(textResponse(first)));
    }

    /**
     * 每次调用返回不同的文本。
     *
     * <p>为什么需要这个：验收 2 要证明两条并行分支「都有产出且互不覆盖」，
     * 如果所有节点返回同一句话，这个断言就永远是绿的——那是在骗自己。
     */
    private void stubModelWithDistinctOutputs() {
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        when(chatModel.call(any(Prompt.class)))
                .thenAnswer(invocation -> textResponse("产出#" + counter.incrementAndGet()));
        when(chatModel.stream(any(Prompt.class)))
                .thenAnswer(invocation -> reactor.core.publisher.Flux.just(
                        textResponse("产出#" + counter.incrementAndGet())));
    }

    private MvcResult call(String path, String question) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/workflow/" + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("question", question, "sessionId", SESSION))))
                .andExpect(status().isOk())
                .andReturn();
        result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        return result;
    }

    private static List<String> nodeNames(MvcResult result) throws Exception {
        var json = new ObjectMapper().readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> names = new ArrayList<>();
        json.get("sequence").forEach(node -> names.add(node.asText()));
        return names;
    }

    @Test
    @DisplayName("sequential_shouldProduceIdenticalNodeSequenceAcrossTwentyRuns")
    void sequential_shouldProduceIdenticalNodeSequenceAcrossTwentyRuns() throws Exception {
        stubModel("这是节点的产出。");

        List<String> first = null;
        Set<List<String>> distinct = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            List<String> names = nodeNames(call("sequential", "我这台 X1 送修大概要多久？"));
            if (first == null) {
                first = names;
            }
            distinct.add(names);
        }

        // 验收 1：二十次调用，节点序列完全一致 —— 顺序是结构给的，不是采样给的
        assertThat(distinct).hasSize(1);
        assertThat(first).contains("understand", "retrieve", "answer");
    }

    @Test
    @DisplayName("parallel_shouldKeepBothBranchOutputsInTheirOwnKeys")
    void parallel_shouldKeepBothBranchOutputsInTheirOwnKeys() throws Exception {
        // 每个节点产出不同文本：这样才能证明两条分支的结果真的各写各的，而不是「看起来都在」
        stubModelWithDistinctOutputs();

        MvcResult result = call("parallel", "展厅这周三还有位置吗？顺便说说参观政策。");
        var json = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        Map<String, String> outputs = new java.util.LinkedHashMap<>();
        json.get("outputs").fields().forEachRemaining(entry -> outputs.put(entry.getKey(), entry.getValue().asText()));

        // 验收 2：两条分支各有独立产出，且互不覆盖（键名冲突是「并行改造后只剩一边」的真根因）
        assertThat(outputs).containsKeys("knowledge_hits", "biz_records");
        assertThat(outputs.get("knowledge_hits")).isNotBlank();
        assertThat(outputs.get("biz_records")).isNotBlank();
        assertThat(outputs.get("knowledge_hits")).isNotEqualTo(outputs.get("biz_records"));
        // 归并节点是显式节点：两份材料都要进它的输入
        assertThat(outputs).containsKey("merged_material");

        List<String> names = nodeNames(result);
        assertThat(names).contains("knowledge-query", "biz-query", "merge");
    }

    /** 路由模式：call 被路由节点用来做分类，stream 被分支节点用来生成回答。 */
    private void stubRouted(String decision, String branchAnswer) {
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse(decision));
        when(chatModel.stream(any(Prompt.class)))
                .thenAnswer(invocation -> reactor.core.publisher.Flux.just(textResponse(branchAnswer)));
    }

    @Test
    @DisplayName("routed_shouldFollowTheSingleClassificationResult")
    void routed_shouldFollowTheSingleClassificationResult() throws Exception {
        // 路由节点要的是 {"agents":[{"agent":...,"query":...}]} 这个 JSON 契约（框架的 BeanOutputConverter）
        stubRouted("{\"agents\":[{\"agent\":\"presale\",\"query\":\"支持私有化部署吗\"}]}",
                "支持私有化部署，具体看知识库规模与并发要求。");
        assertThat(branchOf(call("ask", "你们的数字人支持私有化部署吗？"))).isEqualTo("presale");

        resetModel();
        stubRouted("{\"agents\":[{\"agent\":\"aftersale\",\"query\":\"余位还有多少\"}]}",
                "周三下午已经约满了。");
        assertThat(branchOf(call("ask", "帮我查下展厅预约还有余位吗"))).isEqualTo("aftersale");
    }

    @Test
    @DisplayName("routed_shouldFallBackWhenModelPicksAnUnknownBranch")
    void routed_shouldFallBackWhenModelPicksAnUnknownBranch() throws Exception {
        // 模型给出一个不存在的分支名：框架**不是**走 fallbackAgent，而是抛
        // 「Failed to get valid decision after N retries」（实测），所以出口必须由我们接住
        stubRouted("{\"agents\":[{\"agent\":\"not_a_branch\",\"query\":\"x\"}]}",
                "我不负责这类问题，你可以问我产品能力或展厅预约余位。");

        var json = objectMapper.readTree(
                call("ask", "帮我写一首关于夏天的诗").getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));
        // 边界之外必须留出口：不属于任何分支的问题不能被硬塞进某个分支，也不能变成 500
        assertThat(json.get("branch").asText()).isEqualTo("fallback");
        assertThat(json.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("loop_shouldRunBothNodesAndStopAtConfiguredMaxRounds")
    void loop_shouldRunBothNodesAndStopAtConfiguredMaxRounds() throws Exception {
        stubModel("缺失：展厅名与日期");

        MvcResult result = call("loop", "帮我看看还有位置吗");
        var json = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        Map<String, String> outputs = new java.util.LinkedHashMap<>();
        json.get("outputs").fields().forEachRemaining(entry -> outputs.put(entry.getKey(), entry.getValue().asText()));

        // 两个节点都要真的跑过：LoopAgent 只收一个 subAgent，写错时 completeness 会整个缺失
        assertThat(outputs).containsKeys("completeness", "clarify");

        // 循环体跑的轮数＝配置的上界：序列视图里 check-completeness 出现几次，就是跑了几轮
        List<String> sequence = new java.util.ArrayList<>();
        json.get("sequence").forEach(node -> sequence.add(node.asText()));
        long rounds = sequence.stream().filter("check-completeness"::equals).count();
        assertThat(rounds).isEqualTo(LOOP_ROUNDS);
        assertThat(sequence.stream().filter("ask-back"::equals).count()).isEqualTo(LOOP_ROUNDS);
        assertThat(json.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("everyResponse_shouldExposeNodeNamesAndTimings")
    void everyResponse_shouldExposeNodeNamesAndTimings() throws Exception {
        stubModel("这是一段回答。");

        var json = objectMapper.readTree(
                call("sequential", "随便问一句").getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 验收 4：每次响应都能拿到经过的节点名与各自耗时
        assertThat(json.get("nodes").size()).isGreaterThan(0);
        json.get("nodes").forEach(node -> {
            assertThat(node.get("node").asText()).isNotBlank();
            assertThat(node.get("elapsedMs").asLong()).isGreaterThanOrEqualTo(0);
            // 耗时是「边到边记」的：采集时机写错时每个节点都会是 0 条输出（真实验收踩过）
            assertThat(node.get("emissions").asInt()).isGreaterThan(0);
        });
        assertThat(json.get("sequence").size()).isGreaterThan(0);
        assertThat(json.get("traceId").asText()).hasSize(12);
        assertThat(json.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("nodes_shouldOnlyReceiveTheToolsTheyAreAllowedToUse")
    void nodes_shouldOnlyReceiveTheToolsTheyAreAllowedToUse() {
        // 节点拿什么能力是结构问题：靠 instruction 说「你只能检索」是不够的，
        // 所以这里钉住「按名字裁剪工具」这件事本身
        List<String> knowledgeOnly = names(projectScopedTools.callbacksFor(projectId, SESSION, "trace-t1",
                "knowledge_search"));
        assertThat(knowledgeOnly).containsExactly("knowledge_search");

        List<String> bizOnly = names(projectScopedTools.callbacksFor(projectId, SESSION, "trace-t2",
                "session_stats", "project_info"));
        assertThat(bizOnly).containsExactlyInAnyOrder("session_stats", "project_info");
    }

    private static List<String> names(org.springframework.ai.tool.ToolCallback[] callbacks) {
        return java.util.Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }

    private String branchOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("branch").asText();
    }
}
