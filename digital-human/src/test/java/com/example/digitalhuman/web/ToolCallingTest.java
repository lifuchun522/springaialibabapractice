package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.domain.PendingTitleChange;
import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.DigitalHumanProjectRepository;
import com.example.digitalhuman.repository.PendingTitleChangeRepository;
import com.example.digitalhuman.repository.ToolCallAuditRepository;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.service.ProjectService;
import com.example.digitalhuman.tools.ToolContextKeys;
import com.example.digitalhuman.tools.ToolRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工具的执行与门禁。
 *
 * <p>为什么这里不测「模型发起工具调用」的循环：**那个循环在真实 ChatModel 实现内部**
 * （OpenAiChatModel 持有 ToolCallingManager），ChatClient 本身不执行工具。
 * 用假的 ChatModel 去测，测的其实是自己编的循环，没有意义。
 * 所以这里测的是我们自己拥有的部分——工具拿到参数后的行为与门禁；
 * 而「模型真的会选工具、会填参数」这件事，由真实模型的端到端验证来证明（见 docs/ch06-验收记录.md）。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ToolCallingTest {

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
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolCallAuditRepository audits;

    @Autowired
    private PendingTitleChangeRepository pendingChanges;

    @Autowired
    private DigitalHumanProjectRepository projects;

    private long projectId;
    private Long ownerId;
    private String token;

    @BeforeAll
    void prepareOwnerAndProject() {
        authService.register("tool-owner", "password123");
        AuthService.AuthToken auth = authService.login("tool-owner", "password123");
        ownerId = auth.userId();
        token = auth.token();

        DigitalHumanProject project = projectService.create(ownerId, new ProjectService.ProjectCommand(
                "工具演示", "数字人小器", "#2F6BFF", null, "你好", "再见", null, "你是数字人小器。"));
        projectId = project.getId();

        // 造 3 条真实账本记录，让统计类工具有真实数据可算
        ConversationId conversationId = ConversationId.of(ownerId, projectId, "tool-s1");
        ledgerService.append(projectId, conversationId, ownerId, ChatMessage.Role.USER, "第一句", ChatMessage.Status.COMPLETED);
        ledgerService.append(projectId, conversationId, ownerId, ChatMessage.Role.ASSISTANT, "第二句", ChatMessage.Status.COMPLETED);
        ledgerService.append(projectId, conversationId, ownerId, ChatMessage.Role.USER, "第三句", ChatMessage.Status.COMPLETED);
    }

    private ToolContext context(String sessionId) {
        return new ToolContext(Map.of(
                ToolContextKeys.PROJECT_ID, projectId,
                ToolContextKeys.USER_ID, ownerId,
                ToolContextKeys.SESSION_ID, sessionId,
                ToolContextKeys.CONVERSATION_ID, ownerId + ":" + projectId + ":" + sessionId,
                ToolContextKeys.TRACE_ID, "trace-" + sessionId));
    }

    private ToolCallback callback(ToolCallback[] callbacks, String name) {
        return java.util.Arrays.stream(callbacks)
                .filter(candidate -> candidate.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有找到工具：" + name));
    }

    @Test
    @DisplayName("readOnlyTool_shouldComputeFromRealLedgerAndWriteAudit")
    void readOnlyTool_shouldComputeFromRealLedgerAndWriteAudit() {
        // 模型写的是参数（metric 来自 Schema 白名单），身份从 ToolContext 旁路来
        String result = callback(toolRegistry.readOnly(), "querySessionStats")
                .call("{\"metric\":\"MESSAGE_COUNT\"}", context("tool-s1"));

        assertThat(result).contains("3 条消息");

        List<ToolCallAudit> auditRows = audits.findByProjectIdOrderByIdDesc(projectId);
        assertThat(auditRows).isNotEmpty();
        ToolCallAudit latest = auditRows.get(0);
        assertThat(latest.getToolName()).isEqualTo("querySessionStats");
        assertThat(latest.getStatus()).isEqualTo(ToolCallAudit.Status.OK);
        assertThat(latest.getProjectId()).isEqualTo(projectId);
        assertThat(latest.getCallerUserId()).isEqualTo(ownerId);
        assertThat(latest.getTraceId()).isEqualTo("trace-tool-s1");
        assertThat(latest.getArguments()).contains("MESSAGE_COUNT");
        assertThat(latest.getElapsedMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("readOnlyTool_shouldRefuseWhenIdentityIsMissing")
    void readOnlyTool_shouldRefuseWhenIdentityIsMissing() {
        // 没有身份就不执行：这条边界是代码守的，不是靠提示词
        String result = callback(toolRegistry.readOnly(), "querySessionStats")
                .call("{\"metric\":\"MESSAGE_COUNT\"}", new ToolContext(Map.of()));

        assertThat(result).contains("工具执行失败");
        // 这次调用没有项目身份，审计行里的 project_id 为空，所以按主键查最近一条
        List<ToolCallAudit> all = audits.findAll();
        assertThat(all.get(all.size() - 1).getStatus()).isEqualTo(ToolCallAudit.Status.ERROR);
        assertThat(all.get(all.size() - 1).getProjectId()).isNull();
    }

    @Test
    @DisplayName("writeTool_shouldNotTouchBusinessDataUntilHumanConfirms")
    void writeTool_shouldNotTouchBusinessDataUntilHumanConfirms() throws Exception {
        String before = projects.findById(projectId).orElseThrow().getTitle();
        int pendingBefore = pendingChanges.findByProjectIdAndStatusOrderByIdDesc(
                projectId, PendingTitleChange.Status.PENDING).size();

        // 模型提议改标题：只允许生成待确认记录
        String proposal = callback(toolRegistry.write(), "proposeTitleChange")
                .call("{\"newTitle\":\"被模型改的标题\"}", context("tool-s2"));

        assertThat(proposal).contains("待确认变更").contains("确认令牌");
        assertThat(projects.findById(projectId).orElseThrow().getTitle()).isEqualTo(before);

        List<PendingTitleChange> pending = pendingChanges.findByProjectIdAndStatusOrderByIdDesc(
                projectId, PendingTitleChange.Status.PENDING);
        assertThat(pending).hasSize(pendingBefore + 1);
        String confirmToken = pending.get(0).getConfirmToken();
        assertThat(confirmToken).hasSize(16);

        // 人类确认后才真正落库
        mockMvc.perform(post("/api/projects/" + projectId + "/pending-changes/" + confirmToken + "/confirm")
                        .header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("被模型改的标题"));

        // 令牌单次有效：重复使用被拒绝，且不会再改一次库
        mockMvc.perform(post("/api/projects/" + projectId + "/pending-changes/" + confirmToken + "/confirm")
                        .header("X-Token", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("CONFIRMATION_REJECTED"));
    }

    @Test
    @DisplayName("writeTool_shouldRecordRejectedForForeignKeyToken")
    void writeTool_shouldRecordRejectedForForeignKeyToken() {
        // 拿别的项目的令牌来用：拒绝，且不产生任何变更
        String result = callback(toolRegistry.write(), "proposeTitleChange")
                .call("{\"newTitle\":\"偷改\",\"confirmToken\":\"not-a-real-token\"}", context("tool-s3"));

        assertThat(result).contains("确认失败").contains("没有产生任何数据变更");
        assertThat(projects.findById(projectId).orElseThrow().getTitle()).isNotEqualTo("偷改");
    }

    @Test
    @DisplayName("writeTool_shouldRefuseAnonymousCaller")
    void writeTool_shouldRefuseAnonymousCaller() {
        // 没有登录身份时不给写工具用：拒绝，且不产生待确认记录
        int pendingBefore = pendingChanges.findByProjectIdAndStatusOrderByIdDesc(
                projectId, PendingTitleChange.Status.PENDING).size();

        String result = callback(toolRegistry.write(), "proposeTitleChange").call(
                "{\"newTitle\":\"匿名改的标题\"}",
                new ToolContext(Map.of(ToolContextKeys.PROJECT_ID, projectId, ToolContextKeys.TRACE_ID, "trace-anon")));

        assertThat(result).contains("写操作被拒绝");
        assertThat(pendingChanges.findByProjectIdAndStatusOrderByIdDesc(
                projectId, PendingTitleChange.Status.PENDING)).hasSize(pendingBefore);
    }

    @Test
    @DisplayName("toolAudits_shouldBeQueryableByOwnerOnly")
    void toolAudits_shouldBeQueryableByOwnerOnly() throws Exception {
        callback(toolRegistry.readOnly(), "getProjectInfo").call("{}", context("tool-s4"));

        mockMvc.perform(get("/api/projects/" + projectId + "/tool-audits").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toolName").value("getProjectInfo"))
                .andExpect(jsonPath("$[0].traceId").value("trace-tool-s4"));

        mockMvc.perform(get("/api/projects/" + projectId + "/tool-audits"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pendingChanges_shouldBeVisibleToOwner")
    void pendingChanges_shouldBeVisibleToOwner() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/pending-changes").header("X-Token", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/pending-changes"))
                .andExpect(status().isBadRequest());
    }
}
