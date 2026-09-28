package com.example.digitalhuman.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.example.digitalhuman.ai.ChatOptionsFactory;
import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.config.DigitalHumanChatProperties;
import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.tools.ToolContextKeys;
import com.example.digitalhuman.tools.ToolRegistry;

import reactor.core.publisher.Flux;

/**
 * 一次对话的组装点。
 *
 * <p>三件事在这里汇合，边界写清楚：
 * <ul>
 *   <li><b>人设与模型参数</b>来自项目配置（第 3、4 掌）；</li>
 *   <li><b>短期记忆</b>交给 MessageChatMemoryAdvisor，业务代码只给 conversationId；</li>
 *   <li><b>产品历史</b>单独落 chat_message 账本，开始/完成/取消/失败都留一笔。</li>
 * </ul>
 *
 * <p>业务层不 catch 任何一家 SDK 的异常体系，底层异常统一收敛成 {@link ModelInvocationException}。
 */
@Service
public class DigitalHumanChatService {

    private static final String DEFAULT_PROVIDER = "default";
    /** 未绑定项目的对话（第 1 掌的默认人设路径）在账本里归到项目 0。 */
    private static final long NO_PROJECT = 0L;

    private final ChatClient chatClient;
    private final AgentConfigRepository agentConfigs;
    private final DigitalHumanChatProperties chatProperties;
    private final ChatOptionsFactory chatOptionsFactory;
    private final ChatLedgerService ledger;
    private final ConversationGuard conversationGuard;
    private final ToolRegistry toolRegistry;
    /** 第 17 掌：观测入口，模型/工具/RAG/图/远程调用都从它进调用树与指标。 */
    private final com.example.digitalhuman.observability.ObservedOperation observed;

    public DigitalHumanChatService(ChatClient chatClient,
                                   AgentConfigRepository agentConfigs,
                                   DigitalHumanChatProperties chatProperties,
                                   ChatOptionsFactory chatOptionsFactory,
                                   ChatLedgerService ledger,
                                   ConversationGuard conversationGuard,
                                   ToolRegistry toolRegistry,
                                   com.example.digitalhuman.observability.ObservedOperation observed) {
        this.chatClient = chatClient;
        this.agentConfigs = agentConfigs;
        this.chatProperties = chatProperties;
        this.chatOptionsFactory = chatOptionsFactory;
        this.ledger = ledger;
        this.conversationGuard = conversationGuard;
        this.toolRegistry = toolRegistry;
        this.observed = observed;
    }

    /** 阻塞式一问一答（运行页的 GET 接口、Bridge 端点走这里）。 */
    public String answer(ConversationRequest request) {
        ConversationId conversationId = request.conversationId();
        AgentConfig config = configOf(request.projectId());
        ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                ChatMessage.Role.USER, request.text(), ChatMessage.Status.COMPLETED);

        String provider = config == null ? DEFAULT_PROVIDER : config.getProvider();
        String model = config == null ? DEFAULT_PROVIDER : config.getModel();
        try {
            // 第 17 掌：模型调用是这条链路的第一个「必经之路」。观测名与 layer 固定，
            // 指标按 layer 聚合，诊断面按 traceId 还原——同一份埋点喂两个下游。
            String content = observed.observe("genai.chat", "model",
                    Map.of("provider", provider, "model", model),
                    () -> invoke(spec(config, request), request.text(), provider, model));
            ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                    ChatMessage.Role.ASSISTANT, content, ChatMessage.Status.COMPLETED);
            return content;
        } catch (ModelInvocationException ex) {
            // 失败也留档：历史里看不到「为什么这条没有下文」，排查就只能靠猜
            ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                    ChatMessage.Role.ASSISTANT, errorSummary(ex), ChatMessage.Status.FAILED);
            throw ex;
        }
    }

    /**
     * 流式输出：上游 token 到达即向下游推送。
     *
     * <p>取消不是「什么都没发生」：客户端断开时把已收到的半截内容按 CANCELLED 落账
     * （{@code doOnCancel}），否则产品历史里会出现一条永远没有回复的提问，而没人知道原因。
     *
     * <p>注意：这里的落库是阻塞式 JPA 调用，放在响应式回调里；本掌先保证语义正确，
     * 生产化时再换成异步写入或 R2DBC（见 docs/ch05-验收记录.md 遗留问题）。
     */
    public Flux<String> stream(ConversationRequest request) {
        ConversationId conversationId = request.conversationId();
        if (!conversationGuard.tryAcquire(conversationId.value())) {
            throw new ConversationBusyException(conversationId.value());
        }

        AgentConfig config = configOf(request.projectId());
        ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                ChatMessage.Role.USER, request.text(), ChatMessage.Status.COMPLETED);

        // 空流必须显式失败：这是第 16 掌真实验收抓到的一条不一致——
        // 阻塞式路径遇到「HTTP 成功但内容为空」会抛 EMPTY_RESPONSE，而流式路径
        // 当时把它当成「正常答完了」，账本记 COMPLETED、前端收到一条空消息。
        // 同一个语义在两条路径上表现不同，就是交付契约没有对齐；
        // 而对前端来说，「没有回答」和「回答是空的」是完全不同的两件事：
        // 前者要重试提示，后者会静默地什么也不显示。
        StringBuilder received = new StringBuilder();
        String provider = config == null ? DEFAULT_PROVIDER : config.getProvider();
        String model = config == null ? DEFAULT_PROVIDER : config.getModel();
        // 第 17 掌：流式路径同样要进调用树与指标。第一版只埋了阻塞式，
        // 结果「流式请求在诊断面上是空的」——而运行页走的正是流式（实测发现）。
        return observed.<Flux<String>>observe("genai.chat.stream", "model",
                        Map.of("provider", provider, "model", model),
                        () -> spec(config, request).user(request.text())
                .stream()
                .content()
                .doOnNext(received::append)
                .switchIfEmpty(Flux.defer(() -> Flux.error(new ModelInvocationException(
                        ModelInvocationException.Kind.EMPTY_RESPONSE,
                        config == null ? DEFAULT_PROVIDER : config.getProvider(),
                        config == null ? DEFAULT_PROVIDER : config.getModel(),
                        "模型返回了空内容（流式响应里没有任何片段）", null))))
                .doOnComplete(() -> ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                        ChatMessage.Role.ASSISTANT, received.toString(), ChatMessage.Status.COMPLETED))
                .doOnCancel(() -> ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                        ChatMessage.Role.ASSISTANT, received.toString(), ChatMessage.Status.CANCELLED))
                .onErrorMap(ex -> toModelInvocationException(ex))
                .doOnError(ex -> ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                        ChatMessage.Role.ASSISTANT, errorSummary(ex), ChatMessage.Status.FAILED))
                .doFinally(signal -> conversationGuard.release(conversationId.value())));
    }

    private ChatClient.ChatClientRequestSpec spec(AgentConfig config, ConversationRequest request) {
        ConversationId conversationId = request.conversationId();
        ChatClient.ChatClientRequestSpec spec = config == null
                ? chatClient.prompt().system(chatProperties.defaultSystem())
                : chatClient.prompt().system(config.getSystemPrompt())
                        .options(chatOptionsFactory.optionsFor(config));

        // 只读工具默认交给模型；写工具必须被显式请求，且自身还有确认令牌这道门禁。
        // 注意：这里传的是已经包好审计与超时的 ToolCallback（用 toolCallbacks 而不是 tools——
        // tools() 只接受带 @Tool 注解的对象）
        spec = spec.toolCallbacks(toolCallbacks(request.allowWrite()));

        // 记忆的读写发生在 Advisor 里，这里只声明「这是哪个会话」；
        // 身份走 ToolContext 旁路，模型看不见也填不了。
        // 没有登录身份时**不塞假值**：缺失就必须是缺失，否则写工具会拿着一个假 owner 建出待确认记录
        Map<String, Object> toolContext = new java.util.HashMap<>();
        toolContext.put(ToolContextKeys.PROJECT_ID, ledgerProjectId(request));
        toolContext.put(ToolContextKeys.SESSION_ID, conversationId.sessionId());
        toolContext.put(ToolContextKeys.CONVERSATION_ID, conversationId.value());
        toolContext.put(ToolContextKeys.TRACE_ID, request.traceId());
        if (request.userId() != null) {
            toolContext.put(ToolContextKeys.USER_ID, request.userId());
        }

        return spec.advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId.value()))
                .toolContext(toolContext);
    }

    /**
     * 交给模型的工具集：远程（MCP）与本地只读始终在；本地写工具只在被显式请求时加入。
     *
     * <p>远程工具默认挂载，是因为它的能力本来就不属于我们——不挂，模型就永远答不出预约类问题，
     * 而这类问题在运行页上是用户随手就会问的。
     */
    private org.springframework.ai.tool.ToolCallback[] toolCallbacks(boolean allowWrite) {
        java.util.List<org.springframework.ai.tool.ToolCallback> all = new java.util.ArrayList<>();
        all.addAll(java.util.List.of(toolRegistry.readOnly()));
        all.addAll(java.util.List.of(toolRegistry.remote()));
        if (allowWrite) {
            all.addAll(java.util.List.of(toolRegistry.write()));
        }
        return all.toArray(org.springframework.ai.tool.ToolCallback[]::new);
    }

    private AgentConfig configOf(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return agentConfigs.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目缺少 Agent 配置：" + projectId));
    }

    private long ledgerProjectId(ConversationRequest request) {
        return request.projectId() == null ? NO_PROJECT : request.projectId();
    }

    private static String errorSummary(Throwable ex) {
        if (ex instanceof ModelInvocationException invocation) {
            return "[" + invocation.kind().name() + "] " + invocation.getMessage();
        }
        return "[" + ex.getClass().getSimpleName() + "] " + ex.getMessage();
    }

    private String invoke(ChatClient.ChatClientRequestSpec spec, String question,
                          String provider, String model) {
        String content;
        try {
            content = spec.user(question.trim()).call().content();
        } catch (RuntimeException ex) {
            if (ex instanceof ModelInvocationException invocation) {
                throw invocation;
            }
            throw (RuntimeException) toModelInvocationException(ex, provider, model);
        }

        if (content == null || content.isBlank()) {
            // 200 但内容为空：最容易伪装成「成功」的一种失败，必须显式失败
            throw new ModelInvocationException(ModelInvocationException.Kind.EMPTY_RESPONSE,
                    provider, model, "模型返回了空内容（HTTP 成功但没有有效回复）", null);
        }
        return content;
    }

    private Throwable toModelInvocationException(Throwable ex) {
        return toModelInvocationException(ex, DEFAULT_PROVIDER, DEFAULT_PROVIDER);
    }

    private Throwable toModelInvocationException(Throwable ex, String provider, String model) {
        if (ex instanceof ModelInvocationException) {
            return ex;
        }
        if (ex instanceof NonTransientAiException nonTransient) {
            return new ModelInvocationException(isAuthFailure(nonTransient)
                    ? ModelInvocationException.Kind.AUTH : ModelInvocationException.Kind.PROVIDER_ERROR,
                    provider, model, "模型提供方拒绝了这次调用：" + nonTransient.getMessage(), nonTransient);
        }
        if (ex instanceof TransientAiException transientEx) {
            return new ModelInvocationException(ModelInvocationException.Kind.UNAVAILABLE,
                    provider, model, "模型提供方暂时不可用：" + transientEx.getMessage(), transientEx);
        }
        if (ex instanceof ResourceAccessException accessException) {
            return new ModelInvocationException(ModelInvocationException.Kind.TIMEOUT,
                    provider, model, "调用模型超时或网络不可达：" + accessException.getMessage(), accessException);
        }
        return ex;
    }

    private static boolean isAuthFailure(NonTransientAiException ex) {
        String message = String.valueOf(ex.getMessage());
        return message.contains("401") || message.contains("InvalidApiKey")
                || message.contains("invalid_api_key") || message.contains("Incorrect API key");
    }
}
