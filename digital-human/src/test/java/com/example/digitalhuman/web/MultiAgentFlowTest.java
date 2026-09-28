package com.example.digitalhuman.web;

import java.nio.charset.StandardCharsets;
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
import com.example.digitalhuman.multiagent.MultiAgentRoles;
import com.example.digitalhuman.multiagent.MultiAgentService;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 12 掌三条验收标准里能在离线环境证明的部分。
 *
 * <p>这一掌最容易「看起来很对」：三个角色名字不一样、提示词不一样，看起来就拆开了。
 * 真正要钉住的是两条**硬边界**：
 * <ul>
 *   <li>任何两个角色不共享工具（共用工具＝候选空间没变窄＝白拆）；</li>
 *   <li>三个角色各一份记忆（共用记忆＝上一轮的检索片段污染这一轮的查询参数）。</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiAgentFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MultiAgentService multiAgentService;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;

    @BeforeAll
    void prepareProject() {
        authService.register("multi-owner", "password123");
        AuthService.AuthToken auth = authService.login("multi-owner", "password123");
        DigitalHumanProject project = projectService.create(auth.userId(), new ProjectService.ProjectCommand(
                "多 Agent 演示", "数字人小分", "#2F6BFF", null, "你好", "再见", null, "你是数字人小分。"));
        projectId = project.getId();
    }

    @BeforeEach
    void resetModel() {
        org.mockito.Mockito.reset(chatModel);
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** 按顺序返回：第一次是 Router 的分类，后面依次是各角色的回复。 */
    private void stubConversation(String... replies) {
        ChatResponse[] responses = java.util.Arrays.stream(replies).map(MultiAgentFlowTest::textResponse)
                .toArray(ChatResponse[]::new);
        when(chatModel.call(any(Prompt.class))).thenReturn(responses[0],
                java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }

    private MvcResult ask(String question, String sessionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/multi-agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("question", question, "sessionId", sessionId))))
                .andExpect(status().isOk())
                .andReturn();
        result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        return result;
    }

    @Test
    @DisplayName("roles_shouldNotShareAnyTool")
    void roles_shouldNotShareAnyTool() {
        // 硬约束：工具归属互不重叠。共用工具意味着「每个角色单次决策的候选空间」没有变窄，
        // 拆多 Agent 的第一收益（上下文更窄）就没了，只剩多次调用的成本。
        assertThat(MultiAgentService.toolsAreDisjoint()).isTrue();
        assertThat(MultiAgentRoles.toolsOf(MultiAgentRoles.RECEPTION)).containsExactly("project_info");
        assertThat(MultiAgentRoles.toolsOf(MultiAgentRoles.KNOWLEDGE)).containsExactly("knowledge_search");
        assertThat(MultiAgentRoles.toolsOf(MultiAgentRoles.BUSINESS))
                .containsExactlyInAnyOrder("session_stats", "showroom_query_availability");
    }

    @Test
    @DisplayName("ask_shouldRouteThenHandoffAcrossRoles")
    void ask_shouldRouteThenHandoffAcrossRoles() throws Exception {
        // Router → reception；接待不自己答，交接给 knowledge；知识角色作答
        stubConversation("reception",
                "你好，这个问题我请知识角色来回答。\nhandoff=knowledge",
                "资料里写着：展厅工作日九点到十八点开放，周一闭馆。[1]");

        var json = objectMapper.readTree(
                ask("展厅的开放时间是什么？", "multi-s1").getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));

        // 验收第一条：三个角色能在同一条链路里协作，一次会话内完成流转
        assertThat(json.get("route").asText()).isEqualTo("reception");
        List<String> hops = new java.util.ArrayList<>();
        json.get("hops").forEach(node -> hops.add(node.asText()));
        assertThat(hops).containsExactly("reception", "knowledge");
        // 交接标记不能泄漏给用户
        assertThat(json.get("reply").asText()).doesNotContain("handoff");
        assertThat(json.get("reply").asText()).contains("九点到十八点");
    }

    @Test
    @DisplayName("memory_shouldBeIsolatedPerRole")
    void memory_shouldBeIsolatedPerRole() throws Exception {
        stubConversation("knowledge", "资料里没有相关内容。");
        ask("你们有哪些产品资料？", "multi-s2");

        // 验收第二条的一半：只有被调用的那个角色记忆里多了内容，其它角色看不到这一轮
        assertThat(multiAgentService.memorySizes("multi-s2"))
                .containsEntry("knowledge", 2)     // 用户问 + 自己答
                .containsEntry("reception", 0)
                .containsEntry("business", 0);
    }

    @Test
    @DisplayName("maxHops_shouldBoundTheHandoffChain")
    void maxHops_shouldBoundTheHandoffChain() throws Exception {
        // 每个角色都往外交接：没有上界就会一直跳下去（上界由代码握着，不指望模型自己停）
        stubConversation("reception",
                "handoff=knowledge", "handoff=business", "handoff=reception", "handoff=knowledge");

        var json = objectMapper.readTree(
                ask("随便问一句", "multi-s3").getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));

        List<String> hops = new java.util.ArrayList<>();
        json.get("hops").forEach(node -> hops.add(node.asText()));
        assertThat(hops).hasSizeLessThanOrEqualTo(3);
        // 已经跳过的角色不会被重复跳（否则就是死循环）
        assertThat(hops).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("response_shouldExposeEachRolesToolsSoTheBoundaryIsCheckable")
    void response_shouldExposeEachRolesToolsSoTheBoundaryIsCheckable() throws Exception {
        stubConversation("business", "这个会话共有 3 条消息。");

        var json = objectMapper.readTree(
                ask("这个会话有多少条消息？", "multi-s4").getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));

        // 工具边界要能被外部核对，而不是「文档里说拆开了」
        assertThat(json.get("roleTools").get("reception").get(0).asText()).isEqualTo("project_info");
        assertThat(json.get("roleTools").get("knowledge").get(0).asText()).isEqualTo("knowledge_search");
        assertThat(json.get("route").asText()).isEqualTo("business");
    }
}
