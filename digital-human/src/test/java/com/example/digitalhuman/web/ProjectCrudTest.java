package com.example.digitalhuman.web;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 项目 CRUD、所有权隔离与运行配置读取。 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenOf(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "password123"))));
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createProject(String token, String name, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "title", title,
                                "themeColor", "#FF6B35",
                                "openingLine", "你好，我是" + title,
                                "closingLine", "今天就聊到这里，再见。"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("crud_shouldCreateUpdateListDeleteOwnProject")
    void crud_shouldCreateUpdateListDeleteOwnProject() throws Exception {
        String token = tokenOf("owner1");
        long projectId = createProject(token, "深圳展厅", "数字人小深");

        mockMvc.perform(get("/api/projects").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("数字人小深"))
                .andExpect(jsonPath("$[0].themeColor").value("#FF6B35"));

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "深圳展厅",
                                "title", "数字人小深 v2",
                                "closingLine", "改过的结束语"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("数字人小深 v2"))
                .andExpect(jsonPath("$.closingLine").value("改过的结束语"));

        mockMvc.perform(delete("/api/projects/" + projectId).header("X-Token", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("runtime_shouldExposeProjectAndAgentConfig")
    void runtime_shouldExposeProjectAndAgentConfig() throws Exception {
        String token = tokenOf("owner2");
        long projectId = createProject(token, "政务大厅", "数字人小政");

        mockMvc.perform(get("/api/projects/" + projectId + "/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.title").value("数字人小政"))
                .andExpect(jsonPath("$.project.openingLine").value("你好，我是数字人小政"))
                .andExpect(jsonPath("$.agent.systemPrompt").isNotEmpty())
                .andExpect(jsonPath("$.agent.model").value("deepseek-flash"));
    }

    @Test
    @DisplayName("ownership_shouldHideOtherUsersProject")
    void ownership_shouldHideOtherUsersProject() throws Exception {
        String ownerToken = tokenOf("owner3");
        long projectId = createProject(ownerToken, "私有项目", "不外借");

        String intruderToken = tokenOf("intruder");
        mockMvc.perform(get("/api/projects/" + projectId).header("X-Token", intruderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("X-Token", intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "改别人项目", "title", "不该成功"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("create_shouldRejectBlankNameOrTitle")
    void create_shouldRejectBlankNameOrTitle() throws Exception {
        String token = tokenOf("owner4");

        mockMvc.perform(post("/api/projects")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "  ", "title", "标题"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("logout_shouldInvalidateToken")
    void logout_shouldInvalidateToken() throws Exception {
        String token = tokenOf("owner5");

        mockMvc.perform(post("/api/auth/logout").header("X-Token", token))
                .andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get("/api/projects").header("X-Token", token))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("error");
    }
}
