package com.example.digitalhuman.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * 第 16 掌：生产 profile 的边界是**结构**，不是自觉。
 *
 * <p>文章 06 链 C 的根因写得直白：把「给我看的调试器」和「给用户用的前端」放进同一个可运行产物里。
 * 修法不是「记得别开」，而是让它在生产 profile 下**根本不存在**——
 * 因为靠自觉维持的边界，一定会有人为了排查顺手打开，然后忘记关。
 *
 * <p>本仓库与之对应的风险面是 {@code /internal/llm/v1/**}：一个按设计无鉴权、
 * 协议兼容、能直接驱动模型的入口（给内网语音 Bridge 用的）。
 * 它进了生产路由，就等于把模型能力裸奔在公网上。
 */
@SpringBootTest(properties = {
        // 生产边界测的是「哪些 Bean 被加载」，不是模型调用；占位密钥保证上下文能起来
        "spring.ai.openai.api-key=prod-boundary-test-key-not-a-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class ProdProfileBoundaryTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("prod profile 下内网无鉴权入口不存在：不是「没人知道路径」，而是根本注册不上")
    void internalControllerIsNotLoadedInProd() {
        assertThat(context.getBeanNamesForType(InternalLlmController.class))
                .as("prod 下 InternalLlmController 不应成为 Bean")
                .isEmpty();
    }

    @Test
    @DisplayName("prod profile 下该路径返回 404，而受控契约端点仍在")
    void internalPathIsGoneButContractsRemain() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/llm/v1/chat/completions")
                        .contentType("application/json")
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());

        // 同一份制品里的受控端点照旧存在（否则「关掉边界」就变成了「关掉服务」）
        assertThat(context.getBeanNamesForType(ChatController.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(RunPageController.class)).isNotEmpty();
    }
}
