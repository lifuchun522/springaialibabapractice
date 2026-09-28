package com.example.digitalhuman.web;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运行页问答与 Bridge 端点：模型被换成假的，链路其余部分全是真的。
 * 要验的就一件事——**System Prompt 来自项目配置，而不是写死在代码里**。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectChatAndBridgeTest {

    private static final String PROJECT_PROMPT = "你是深圳本地生活的数字人向导，只答深圳相关。";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @MockitoBean
    private ChatModel chatModel;

    private long projectId;

    @BeforeEach
    void setUp() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("我是数字人小深。")))));
        if (projectId == 0L) {
            DigitalHumanProject project = projectService.create(888L, new ProjectService.ProjectCommand(
                    "本地生活", "数字人小深", null, null, null, null, null, PROJECT_PROMPT));
            projectId = project.getId();
        }
    }

    @Test
    @DisplayName("chat_shouldUseProjectSystemPrompt_whenProjectIdGiven")
    void chat_shouldUseProjectSystemPrompt_whenProjectIdGiven() throws Exception {
        mockMvc.perform(get("/api/chat").param("q", "深圳有什么好玩的").param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("我是数字人小深。"));

        String promptText = capturedPromptText();
        assertThat(promptText).contains(PROJECT_PROMPT);
        assertThat(promptText).contains("深圳有什么好玩的");
    }

    @Test
    @DisplayName("chat_shouldRejectBlankQuestion")
    void chat_shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(get("/api/chat").param("q", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("chat_shouldReturn404_whenProjectHasNoAgentConfig")
    void chat_shouldReturn404_whenProjectHasNoAgentConfig() throws Exception {
        mockMvc.perform(get("/api/chat").param("q", "你好").param("projectId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("bridgeEndpoint_shouldReturnOpenAiCompatibleShape")
    void bridgeEndpoint_shouldReturnOpenAiCompatibleShape() throws Exception {
        String body = """
                {"model":"digital-human",
                 "user":"%d:session-1",
                 "messages":[{"role":"system","content":"ignored"},
                             {"role":"user","content":"给游客推荐一个深圳景点"}]}
                """.formatted(projectId);

        mockMvc.perform(post("/internal/llm/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].message.content").value("我是数字人小深。"))
                .andExpect(jsonPath("$.choices[0].finish_reason").value("stop"));

        assertThat(capturedPromptText()).contains("给游客推荐一个深圳景点");
    }

    @Test
    @DisplayName("bridgeEndpoint_shouldRejectMalformedRequest")
    void bridgeEndpoint_shouldRejectMalformedRequest() throws Exception {
        mockMvc.perform(post("/internal/llm/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/internal/llm/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"" + projectId + ":s1\"}"))
                .andExpect(status().isBadRequest());
    }

    private String capturedPromptText() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.atLeastOnce()).call(captor.capture());
        return captor.getAllValues().stream()
                .flatMap(prompt -> prompt.getInstructions().stream())
                .map(message -> message.getText())
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
