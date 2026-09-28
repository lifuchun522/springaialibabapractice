package com.example.digitalhuman.web;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.service.ProjectService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 三类模型错误必须有确定的响应体——不允许退化成默认 500 页面。
 *
 * <p>再加上「200 但内容为空」这一类：它最容易伪装成成功。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelErrorMappingTest {

    private static final String CHAT_BODY = "{\"text\":\"你好\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;

    @BeforeEach
    void setUp() {
        if (projectId == 0L) {
            DigitalHumanProject project = projectService.create(777L, new ProjectService.ProjectCommand(
                    "错误映射演示", "数字人小错", null, null, null, null, null, "你是数字人小错。"));
            projectId = project.getId();
        }
    }

    private void modelThrows(RuntimeException exception) {
        when(chatModel.call(any(Prompt.class))).thenThrow(exception);
    }

    @Test
    @DisplayName("authFailure_shouldReturn502WithModelAuthType")
    void authFailure_shouldReturn502WithModelAuthType() throws Exception {
        modelThrows(new NonTransientAiException(
                "HTTP 401 - {\"code\":\"InvalidApiKey\",\"message\":\"Invalid API-key provided.\"}"));

        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("MODEL_AUTH"))
                .andExpect(jsonPath("$.provider").value("deepseek"))
                .andExpect(jsonPath("$.model").value("deepseek-flash"))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("providerError_shouldReturn502WithProviderErrorType")
    void providerError_shouldReturn502WithProviderErrorType() throws Exception {
        modelThrows(new NonTransientAiException("HTTP 400 - bad request payload"));

        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("MODEL_PROVIDER_ERROR"));
    }

    @Test
    @DisplayName("timeout_shouldReturn504")
    void timeout_shouldReturn504() throws Exception {
        modelThrows(new ResourceAccessException("Read timed out"));

        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.type").value("MODEL_TIMEOUT"));
    }

    @Test
    @DisplayName("upstreamUnavailable_shouldReturn503")
    void upstreamUnavailable_shouldReturn503() throws Exception {
        modelThrows(new TransientAiException("HTTP 429 - rate limited"));

        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("MODEL_UNAVAILABLE"));
    }

    @Test
    @DisplayName("emptyContent_shouldFailLoudlyInsteadOfPretendingSuccess")
    void emptyContent_shouldFailLoudlyInsteadOfPretendingSuccess() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("")))));

        mockMvc.perform(post("/api/projects/" + projectId + "/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("MODEL_EMPTY_RESPONSE"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("空内容")));
    }
}
