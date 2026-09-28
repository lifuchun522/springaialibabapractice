package com.example.digitalhuman.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.digitalhuman.service.ConversationRequest;
import com.example.digitalhuman.service.DigitalHumanChatService;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * L2 组件层：把 mock 边界从 ChatModel 撤到 <b>HTTP 层</b>。
 *
 * <p>这是第 15 掌最关键的一次改动。原来的写法是 {@code @MockitoBean ChatModel}，
 * 它把被测行为整段抽走——Prompt 怎么拼、工具定义怎么序列化、工具怎么被执行、
 * 历史怎么回灌，全部发生在 mock 的那一侧，所以「单测全绿」证明不了任何链路行为。
 *
 * <p>换成 MockWebServer 之后，<b>我们发出去了什么</b>第一次可断言：
 * 工具定义在不在、System Prompt 有没有带上、身份字段有没有被偷偷塞进 Schema、
 * 历史有没有回灌。这些都是我们能 100% 控制的东西——断言它们，比断言一段随机文本可靠得多，
 * 而且失败时能直接定位到是 Prompt 变了、还是工具定义丢了。
 *
 * <p>边界也要说清楚：桩响应是「我们以为模型会返回的」，不是真实模型行为。
 * 所以这一层只能验证链路正确性，<b>不能</b>验证效果好坏。把这两件事混起来，
 * 会得到一批自信的假绿——那正是第 15 掌要修的病。
 */
@SpringBootTest
@ActiveProfiles("test")
class ModelHttpStubComponentTest {

    /** 静态初始化而不是 @BeforeAll：@DynamicPropertySource 在 @BeforeAll 之前就要拿到端口。 */
    private static final MockWebServer MODEL_SERVER = startServer();

    @Autowired
    private DigitalHumanChatService chatService;

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
        } catch (IOException ex) {
            throw new UncheckedIOException("MockWebServer 启动失败", ex);
        }
        return server;
    }

    @AfterAll
    static void shutdown() throws IOException {
        MODEL_SERVER.shutdown();
    }

    /**
     * 模型通道指向桩服务器。
     *
     * <p>属性名按本仓库实际通道核验：本项目只保留一条模型通道（DeepSeek 走 OpenAI 兼容协议，
     * 见 docs/ch02 的取舍），所以这里替换的是 {@code spring.ai.openai.base-url}，
     * 而不是文章示例里的 {@code spring.ai.dashscope.base-url}——照抄后者在本仓库里不会生效，
     * 请求会真的打到外网去。
     */
    @DynamicPropertySource
    static void pointModelChannelToStub(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> MODEL_SERVER.url("/").toString());
        registry.add("spring.ai.openai.api-key", () -> "stub-key-not-a-secret");
    }

    @Test
    @DisplayName("L2：请求体里必须带工具定义，且身份字段不得出现在工具 Schema 里")
    void shouldSendToolDefinitionsWithoutIdentity() throws Exception {
        MODEL_SERVER.enqueue(chatResponse("chat-simple.json"));

        chatService.answer(new ConversationRequest(null, "s-l2-tools", null, "你好"));

        RecordedRequest request = takeRequest();
        String body = request.getBody().readUtf8();

        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(body).contains("\"tools\"");
        assertThat(body).contains("getProjectInfo");
        assertThat(body).contains("querySessionStats");
        // 身份走 ToolContext 旁路，模型看不见也填不了：它不该出现在请求体里
        assertThat(body).doesNotContain("projectId");
    }

    @Test
    @DisplayName("L2：System Prompt 必须随请求发出")
    void shouldSendSystemPrompt() throws Exception {
        MODEL_SERVER.enqueue(chatResponse("chat-simple.json"));

        chatService.answer(new ConversationRequest(null, "s-l2-system", null, "你好"));

        String body = takeRequest().getBody().readUtf8();
        assertThat(body).contains("数字人主播");
        assertThat(body).contains("\"role\":\"system\"");
    }

    @Test
    @DisplayName("L2：模型名必须来自配置，历史必须回灌到下一轮请求")
    void shouldSendConfiguredModelAndReplayHistory() throws Exception {
        MODEL_SERVER.enqueue(chatResponse("chat-simple.json"));
        chatService.answer(new ConversationRequest(null, "s-l2-history", null, "请记住：我的项目叫磐石。"));
        String firstBody = takeRequest().getBody().readUtf8();
        assertThat(firstBody).contains("deepseek-flash");

        MODEL_SERVER.enqueue(chatResponse("chat-simple.json"));
        chatService.answer(new ConversationRequest(null, "s-l2-history", null, "我的项目叫什么？"));
        String secondBody = takeRequest().getBody().readUtf8();

        assertThat(secondBody).contains("我的项目叫磐石。");
        assertThat(secondBody).contains("我的项目叫什么？");
    }

    private static RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = MODEL_SERVER.takeRequest();
        assertThat(request).isNotNull();
        return request;
    }

    private static MockResponse chatResponse(String stubFile) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(readStub(stubFile));
    }

    /** 桩响应放资源文件而不是内联字符串：JSON 里的转义噪音会把断言本身变成维护负担。 */
    private static String readStub(String name) {
        try (var in = ModelHttpStubComponentTest.class.getClassLoader()
                .getResourceAsStream("stub/" + name)) {
            if (in == null) {
                throw new IllegalStateException("桩响应文件缺失：stub/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取桩响应失败：" + name, ex);
        }
    }
}
