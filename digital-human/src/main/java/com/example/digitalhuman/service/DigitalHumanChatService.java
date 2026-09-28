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

    public DigitalHumanChatService(ChatClient chatClient,
                                   AgentConfigRepository agentConfigs,
                                   DigitalHumanChatProperties chatProperties,
                                   ChatOptionsFactory chatOptionsFactory,
                                   ChatLedgerService ledger,
                                   ConversationGuard conversationGuard,
                                   ToolRegistry toolRegistry) {
        this.chatClient = chatClient;
        this.agentConfigs = agentConfigs;
        this.chatProperties = chatProperties;
        this.chatOptionsFactory = chatOptionsFactory;
        this.ledger = ledger;
        this.conversationGuard = conversationGuard;
        this.toolRegistry = toolRegistry;
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
            String content = invoke(spec(config, request), request.text(), provider, model);
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

        StringBuilder received = new StringBuilder();
        return spec(config, request).user(request.text())
                .stream()
                .content()
                .doOnNext(received::append)
                .doOnComplete(() -> ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                        ChatMessage.Role.ASSISTANT, received.toString(), ChatMessage.Status.COMPLETED))
                .doOnCancel(() -> ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                        ChatMessage.Role.ASSISTANT, received.toString(), ChatMessage.Status.CANCELLED))
                .onErrorMap(ex -> toModelInvocationException(ex))
                .doOnError(ex -> ledger.append(ledgerProjectId(request), conversationId, request.userId(),
                        ChatMessage.Role.ASSISTANT, errorSummary(ex), ChatMessage.Status.FAILED))
                .doFinally(signal -> conversationGuard.release(conversationId.value()));
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

    private org.springframework.ai.tool.ToolCallback[] toolCallbacks(boolean allowWrite) {
        if (!allowWrite) {
            return toolRegistry.readOnly();
        }
        org.springframework.ai.tool.ToolCallback[] readOnly = toolRegistry.readOnly();
        org.springframework.ai.tool.ToolCallback[] write = toolRegistry.write();
        org.springframework.ai.tool.ToolCallback[] all =
                new org.springframework.ai.tool.ToolCallback[readOnly.length + write.length];
        System.arraycopy(readOnly, 0, all, 0, readOnly.length);
        System.arraycopy(write, 0, all, readOnly.length, write.length);
        return all;
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
