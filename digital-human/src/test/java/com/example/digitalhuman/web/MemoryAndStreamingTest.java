package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
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
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationRequest;
import com.example.digitalhuman.service.DigitalHumanChatService;
import com.example.digitalhuman.service.ProjectService;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 5 掌的三条验收线：
 * <ol>
 *   <li>同一会话有上下文，不同会话不串线；</li>
 *   <li>Web 能以 {@code text/event-stream} 拿到增量；</li>
 *   <li>产品历史落在业务账本里，按 projectId/sessionId 查得回，且取消要留痕。</li>
 * </ol>
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryAndStreamingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DigitalHumanChatService chatService;

    @Autowired
    private ChatLedgerService ledgerService;

    @Autowired
    private AuthService authService;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;
    private Long ownerId;
    private String token;

    /** 账号与项目只需建一次；模型桩每个用例都要重打（MockitoBean 会在用例后重置）。 */
    @BeforeAll
    void createOwnerAndProject() {
        authService.register("memory-owner", "password123");
        AuthService.AuthToken auth = authService.login("memory-owner", "password123");
        ownerId = auth.userId();
        token = auth.token();
        DigitalHumanProject project = projectService.create(ownerId, new ProjectService.ProjectCommand(
                "记忆演示", "数字人小忆", null, null, null, null, null, "你是数字人小忆，只答一句话。"));
        projectId = project.getId();
    }

    @BeforeEach
    void stubModel() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("我是深圳的数字人。"));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("深圳"), response("很好逛。")));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private void ask(String sessionId, String text) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", text, "sessionId", sessionId))))
                .andExpect(status().isOk());
    }

    private String lastPromptText() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.atLeastOnce()).call(captor.capture());
        Prompt last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        return last.getInstructions().stream().map(message -> message.getText())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("memory_shouldCarryContextWithinSameSession")
    void memory_shouldCarryContextWithinSameSession() throws Exception {
        ask("mem-s1", "我叫小明");
        ask("mem-s1", "我叫什么名字");

        // 第二轮发给模型的 Prompt 里必须带着第一轮的内容，否则记忆没生效
        assertThat(lastPromptText()).contains("我叫小明");
    }

    @Test
    @DisplayName("memory_shouldNotLeakAcrossSessions")
    void memory_shouldNotLeakAcrossSessions() throws Exception {
        ask("mem-s2", "我叫小明");
        ask("mem-s3", "我叫什么名字");

        // 换了 sessionId 就是另一段上下文：项目隔离不等于会话隔离
        assertThat(lastPromptText()).doesNotContain("我叫小明");
    }

    @Test
    @DisplayName("stream_shouldEmitServerSentEvents")
    void stream_shouldEmitServerSentEvents() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/projects/" + projectId + "/chat/stream")
                        .param("sessionId", "mem-s4")
                        .param("text", "深圳怎么样"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
                .andReturn();

        // SSE 按规范就是 UTF-8；MockMvc 默认按 ISO-8859-1 读，中文会变乱码
        String body = completed.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("data:深圳").contains("data:很好逛。");
    }

    @Test
    @DisplayName("ledger_shouldRecordCompletedTurnAndBeQueryable")
    void ledger_shouldRecordCompletedTurnAndBeQueryable() throws Exception {
        ask("mem-s5", "深圳有什么好玩的");

        mockMvc.perform(get("/api/projects/" + projectId + "/sessions/mem-s5/messages")
                        .header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("深圳有什么好玩的"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("history_shouldRequireToken")
    void history_shouldRequireToken() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/sessions/mem-s5/messages"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("stream_shouldRecordCancelledTurnWhenClientStopsEarly")
    void stream_shouldRecordCancelledTurnWhenClientStopsEarly() {
        // 客户端只取一段就断开：账本里必须留下 CANCELLED，而不是一条没有下文的提问
        chatService.stream(new ConversationRequest(projectId, "mem-cancel", ownerId, "讲个长故事"))
                .take(1)
                .blockLast();

        List<ChatLedgerService.MessageView> history = ledgerService.history(projectId, "mem-cancel").stream()
                .map(ChatLedgerService.MessageView::of)
                .toList();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo("USER");
        assertThat(history.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(history.get(1).status()).isEqualTo("CANCELLED");
        assertThat(history.get(1).content()).isEqualTo("深圳");
    }
}
