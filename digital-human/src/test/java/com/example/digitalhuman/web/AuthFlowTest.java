package com.example.digitalhuman.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 注册与登录的端到端校验（走真实控制器 + 服务 + 内存库，只有模型调用被关在门外）。 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
    }

    @Test
    @DisplayName("register_shouldCreateUser_andRejectDuplicateAndShortPassword")
    void register_shouldCreateUser_andRejectDuplicateAndShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("alice", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.id").isNumber());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("alice", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("bob", "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("login_shouldReturnToken_andRejectBadCredentials")
    void login_shouldReturnToken_andRejectBadCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(credentials("carol", "password123")));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("carol", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNumber());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("carol", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("protectedEndpoint_shouldRejectMissingOrInvalidToken")
    void protectedEndpoint_shouldRejectMissingOrInvalidToken() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects").header("X-Token", "not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
