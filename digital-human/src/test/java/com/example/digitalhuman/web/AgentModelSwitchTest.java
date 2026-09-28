package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 4 掌的核心验收：**换模型是改数据，不是改代码**。
 *
 * <p>同一个接口，只把库里的 provider/model/temperature/maxTokens 改掉，
 * 下一次请求发出去的就是新的模型参数——代码一行没动、服务也没重启。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentModelSwitchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    @MockitoBean
    private ChatModel chatModel;

    private String token;
    private long ownerId;
    private long projectId;

    private String tokenOf(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "password123"))));
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        ownerId = json.get("userId").asLong();
        return json.get("token").asText();
    }

    private void prepare() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("收到。")))));
        if (token != null) {
            return;
        }
        token = tokenOf("model-owner");
        // 项目必须挂在这个登录用户名下，否则改配置会正确地被判成越权（404）
        DigitalHumanProject project = projectService.create(ownerId, new ProjectService.ProjectCommand(
                "模型切换演示", "数字人小模", null, null, null, null, null, "你是数字人小模。"));
        projectId = project.getId();
    }

    @Test
    @DisplayName("catalog_shouldBeServedFromConfiguration")
    void catalog_shouldBeServedFromConfiguration() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deepseek[0].id").value("deepseek-flash"))
                .andExpect(jsonPath("$.deepseek[0].displayName").value("DeepSeek V4.1 Flash"))
                .andExpect(jsonPath("$.deepseek[1].id").value("deepseek-v4-pro"));
    }

    @Test
    @DisplayName("switchModelByData_shouldChangeOptionsSentToModel")
    void switchModelByData_shouldChangeOptionsSentToModel() throws Exception {
        prepare();

        // 第一次：默认 deepseek-flash
        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("收到。"));
        assertThat(capturedOptions().getModel()).isEqualTo("deepseek-flash");

        // 只改数据：换成 deepseek-v4-pro，并把温度与上限一起调掉
        mockMvc.perform(put("/api/projects/" + projectId + "/agent")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"deepseek","model":"deepseek-v4-pro",
                                 "systemPrompt":"你是数字人小模，只答一句话。",
                                 "temperature":0.20,"maxTokens":128}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("deepseek-v4-pro"))
                .andExpect(jsonPath("$.temperature").value(0.20))
                .andExpect(jsonPath("$.maxTokens").value(128));

        // 第二次：同一个接口，发出去的模型参数已经变了
        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"你好\"}"))
                .andExpect(status().isOk());

        OpenAiChatOptions options = capturedOptions();
        assertThat(options.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(options.getTemperature()).isEqualTo(0.20d);
        assertThat(options.getMaxTokens()).isEqualTo(128);
    }

    @Test
    @DisplayName("updateAgent_shouldRejectModelOutsideCatalog")
    void updateAgent_shouldRejectModelOutsideCatalog() throws Exception {
        prepare();

        mockMvc.perform(put("/api/projects/" + projectId + "/agent")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"deepseek\",\"model\":\"gpt-9-不存在\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("MODEL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("不在目录中")));
    }

    @Test
    @DisplayName("updateAgent_shouldRequireTokenAndOwnership")
    void updateAgent_shouldRequireTokenAndOwnership() throws Exception {
        prepare();

        mockMvc.perform(put("/api/projects/" + projectId + "/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"deepseek\",\"model\":\"deepseek-flash\"}"))
                .andExpect(status().isBadRequest());

        String intruder = tokenOf("model-intruder");
        mockMvc.perform(put("/api/projects/" + projectId + "/agent")
                        .header("X-Token", intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"deepseek\",\"model\":\"deepseek-flash\"}"))
                .andExpect(status().isNotFound());
    }

    private OpenAiChatOptions capturedOptions() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.atLeastOnce()).call(captor.capture());
        Prompt last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        return (OpenAiChatOptions) last.getOptions();
    }
}
