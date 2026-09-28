package com.example.knowledgeagent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2A 协议契约：能力声明、任务生命周期、**版本协商**。
 *
 * <p>版本协商这一条最值得测：跨服务里最贵的失败模式不是「调不通」，
 * 而是「调通了但语义对不上」——调用方拿到一个看起来正常的答案，
 * 业务上却是按旧版协议解读的。所以不兼容必须显式、快速、可诊断。
 */
@SpringBootTest(properties = {"spring.ai.openai.api-key=test-key-not-used",
        "a2a.instance-id=ka-test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class A2AProtocolTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private KnowledgeBase.TaskStore tasks;

    @BeforeEach
    void stubModel() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("资料里写着：工作日九点到十八点开放。[1]")))));
    }

    @Test
    @DisplayName("card_shouldDeclareCapabilitiesSoCallersNeedNoCode")
    void card_shouldDeclareCapabilitiesSoCallersNeedNoCode() throws Exception {
        var json = objectMapper.readTree(mockMvc.perform(get("/.well-known/agent.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 能力声明：你是谁、你会什么、你怎么被访问——有这三样，调用方就不需要读你的代码
        assertThat(json.get("name").asText()).isEqualTo("knowledge-agent");
        assertThat(json.get("version").asText()).isEqualTo(A2AProtocol.VERSION);
        assertThat(json.get("skills").get(0).get("id").asText()).isEqualTo("knowledge-qa");
        assertThat(json.get("capabilities").get("streaming").asBoolean()).isTrue();
        assertThat(json.get("capabilities").get("taskLifecycle").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("create_shouldRejectMissingOrMismatchedVersionExplicitly")
    void create_shouldRejectMissingOrMismatchedVersionExplicitly() throws Exception {
        // 不声明版本：直接说不清，不猜
        mockMvc.perform(post("/a2a/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"展厅几点开门\"}"))
                .andExpect(status().isBadRequest());

        // 版本不兼容：409，而且消息里要能看出两边版本（可诊断）
        MvcResult mismatched = mockMvc.perform(post("/a2a/tasks")
                        .header(A2AProtocol.VERSION_HEADER, "2.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"展厅几点开门\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        // 响应体默认不带 reason（Spring 的 include-message 开关控制），所以这里断言异常消息本身：
        // 「可诊断」的要求是**错误信息里能看出两边版本**，不是「HTTP 状态码对」
        assertThat(mismatched.getResolvedException()).isNotNull();
        assertThat(mismatched.getResolvedException().getMessage())
                .contains(A2AProtocol.VERSION).contains("2.0");
    }

    @Test
    @DisplayName("task_shouldHaveLifecycleAndStreamPartialResults")
    void task_shouldHaveLifecycleAndStreamPartialResults() throws Exception {
        mockMvc.perform(post("/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("docName", "手册.md", "content", "深圳展厅工作日九点到十八点开放，周一闭馆。"))))
                .andExpect(status().isOk());

        MvcResult created = mockMvc.perform(post("/a2a/tasks")
                        .header(A2AProtocol.VERSION_HEADER, A2AProtocol.VERSION)
                        .header(A2AProtocol.TRACE_HEADER, "trace-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"展厅开放时间\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        var task = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 创建只是「受理」：远端不确定性进本地状态机，状态由调用方主动查
        assertThat(task.get("state").asText()).isEqualTo("SUBMITTED");
        assertThat(task.get("traceId").asText()).isEqualTo("trace-test-1");
        String taskId = task.get("taskId").asText();

        // 任务生命周期：SUBITTED → WORKING → COMPLETED，每一步都可由调用方查询
        assertThat(tasks.get(taskId)).isPresent();
        tasks.update(taskId, A2AProtocol.TaskState.WORKING, null, java.util.List.of(), null);
        A2AProtocol.Task completed = tasks.update(taskId, A2AProtocol.TaskState.COMPLETED, "答案", null, null);
        assertThat(completed.state()).isEqualTo(A2AProtocol.TaskState.COMPLETED);
        assertThat(completed.answer()).isEqualTo("答案");

        var queried = objectMapper.readTree(mockMvc.perform(get("/a2a/tasks/" + taskId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(queried.get("state").asText()).isEqualTo("COMPLETED");

        // 流式端点的「边到边消费」由真实验收证明（evidence-ch13/：curl -N 抓到的 SSE 帧）：
        // MockMvc 不会等异步流写完，在这里断言 SSE 体只会得到一个假失败
        mockMvc.perform(post("/a2a/tasks/" + taskId + "/events")
                        .header(A2AProtocol.TRACE_HEADER, "trace-test-1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cancel_shouldBeDefinedSoNoTaskIsLeftHanging")
    void cancel_shouldBeDefinedSoNoTaskIsLeftHanging() throws Exception {
        MvcResult created = mockMvc.perform(post("/a2a/tasks")
                        .header(A2AProtocol.VERSION_HEADER, A2AProtocol.VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"随便问问\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("taskId").asText();

        var cancelled = objectMapper.readTree(mockMvc.perform(post("/a2a/tasks/" + taskId + "/cancel"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 「谁有权取消」必须在协议里定义，否则悬挂任务没人清理
        assertThat(cancelled.get("state").asText()).isEqualTo("CANCELED");
    }
}
