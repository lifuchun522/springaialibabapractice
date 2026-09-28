package com.example.digitalhuman.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 运行页：一条可复现的「项目 → 运行配置」链路。 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AgentConfigRepository agentConfigs;

    private long newProject(String title, String openingLine, String closingLine, String systemPrompt) {
        ProjectService.ProjectCommand command = new ProjectService.ProjectCommand(
                "测试项目", title, "#12B886", null, openingLine, closingLine, null, systemPrompt);
        DigitalHumanProject project = projectService.create(999L, command);
        return project.getId();
    }

    @Test
    @DisplayName("runPage_shouldRenderHtmlBoundToProjectId")
    void runPage_shouldRenderHtmlBoundToProjectId() throws Exception {
        long projectId = newProject("数字人小测", "开场白来了", "结束语走了", null);

        mockMvc.perform(get("/run/" + projectId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("数字人运行页")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const PROJECT_ID = Number('" + projectId + "');")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("__PROJECT_ID__"))));
    }

    @Test
    @DisplayName("create_shouldSeedAgentConfigFromApplicationConfig")
    void create_shouldSeedAgentConfigFromApplicationConfig() {
        long projectId = newProject("默认人设", null, null, null);

        var config = agentConfigs.findByProjectId(projectId).orElseThrow();
        assertThat(config.getSystemPrompt()).isEqualTo("你是一个专业的数字人主播，回答简洁友好。");
        assertThat(config.getModel()).isEqualTo("deepseek-flash");
        assertThat(config.getTemperature()).isEqualByComparingTo("0.70");
        assertThat(config.getMaxTokens()).isEqualTo(1024);
    }

    @Test
    @DisplayName("runtime_shouldReturn404ForUnknownProject")
    void runtime_shouldReturn404ForUnknownProject() throws Exception {
        mockMvc.perform(get("/api/projects/999999/runtime"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("update_shouldChangeAgentSystemPromptWithoutRestart")
    void update_shouldChangeAgentSystemPromptWithoutRestart() {
        long projectId = newProject("可改人设", null, null, "初始人设");

        projectService.update(999L, projectId, new ProjectService.ProjectCommand(
                "测试项目", "可改人设", null, null, null, null, null, "改过的人设：你是深圳本地向导。"));

        assertThat(agentConfigs.findByProjectId(projectId).orElseThrow().getSystemPrompt())
                .isEqualTo("改过的人设：你是深圳本地向导。");
    }
}
