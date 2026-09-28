package com.example.digitalhuman.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 第 16 掌：契约本身要被断言。
 *
 * <p>文章 01 章开头，前端同事的第一句问话是「返回字段到底叫 `answer` 还是 `content`」。
 * 这类问题不该靠口头交接，也不该靠前端读后端代码——它应该是一条**改了就红**的断言。
 *
 * <p>这份用例钉三样东西：
 * <ol>
 *   <li>路径 + 方法 + {@code Content-Type}：流式必须是 {@code text/event-stream}，
 *       阻塞式必须是 {@code application/json}（写错了前端拿到一次性响应还以为「流式没生效」）；</li>
 *   <li>DTO 的字段名：record 的分量名就是契约，改名等于破坏性变更，必须显式改测试；</li>
 *   <li>生产边界：内网无鉴权入口在 prod profile 下必须 404。</li>
 * </ol>
 */
@SpringBootTest(properties = {
        // 契约层不调模型，但上下文里要真有 ChatModel 这个 Bean（不能 mock 掉它，
        // 否则「端点是否注册」这件事就不是在被测上下文里验的）。
        // 给一个占位密钥即可，不会产生任何真实调用。
        "spring.ai.openai.api-key=contract-test-key-not-a-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTest {

    /**
     * 按名字取：上下文里不止一个 {@code RequestMappingHandlerMapping}
     * （实测有第二个来自框架侧的注册），按类型注入会直接报 NoUniqueBeanDefinition。
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("流式端点必须声明 text/event-stream：否则前端拿到的是一次性响应")
    void streamingEndpointMustDeclareEventStream() {
        assertThat(producesOf("/api/projects/{id}/chat/stream"))
                .as("第 16 掌契约：流式端点的媒体类型写在 Controller 上，不能靠协商猜")
                .contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    @DisplayName("阻塞式端点必须声明 application/json")
    void blockingEndpointMustDeclareJson() {
        assertThat(producesOf("/api/projects/{id}/chat"))
                .contains(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("DTO 字段名就是契约：改名必须改这里，让破坏性变更无处可藏")
    void dtoFieldNamesAreContract() {
        assertThat(componentNames(ChatController.ChatRequest.class))
                .as("请求体字段：前端的 request body 直接对应它们")
                .containsExactly("text", "sessionId", "allowWrite");
        assertThat(componentNames(ChatController.ChatReply.class))
                .as("响应体字段：前端渲染打字机的第一条数据来自这里")
                .containsExactly("reply");
    }

    @Test
    @DisplayName("内网无鉴权入口在 prod profile 下必须不可达（12.4 那条边界的可执行版本）")
    void internalEndpointMustBeAbsentInProd() throws Exception {
        // 本测试用默认（非 prod）profile：入口存在，属于开发/内网形态
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/llm/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("非 prod 下入口存在：应当是可解析的 4xx（缺 messages/user），而不是 404")
                        .isNotEqualTo(404));
    }

    private List<String> producesOf(String pattern) {
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            boolean matches = info.getPathPatternsCondition().getPatternValues().stream()
                    .anyMatch(value -> value.equals(pattern));
            if (matches && info.getProducesCondition() != null) {
                return info.getProducesCondition().getProducibleMediaTypes().stream()
                        .map(MediaType::toString)
                        .toList();
            }
        }
        return List.of();
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
