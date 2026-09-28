package com.example.digitalhuman.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.tools.ToolContextKeys;
import com.example.digitalhuman.tools.ToolRegistry;

/**
 * 数字人 Agent 服务：把一次用户请求交给 ReactAgent，并交回**一个字符串**。
 *
 * <p>为什么按请求构建 Agent：本仓库的只读工具需要「项目身份」，而身份不能由模型填（第 6 掌的规矩）。
 * 框架的 toolContext 是构建期参数，所以这里用「请求内作用域的工具对象」把身份闭包进去。
 *
 * <p><b>代价写清楚</b>：每次都重新构建 Agent（含一次图编译），比复用 Agent 贵；
 * 正确的长期做法是让框架支持按调用传身份（或按项目缓存 Agent 实例），
 * 这条留在 docs/ch09-验收记录.md 的遗留问题里，不假装它不存在。
 *
 * <p><b>这里挂了什么、没挂什么</b>：边界（模型调用上限）用框架的 Hook，
 * 因为它必须站在图节点这一层才看得见「循环」；工具失败策略不用框架的重试拦截器，
 * 因为失败已经在工具边界被转成可读结果，外层拦截器永远看不到失败（详见 AuditingToolCallback）。
 */
@Service
public class DigitalHumanAgentService {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanAgentService.class);

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final DigitalHumanAgentProperties properties;
    private final ProjectScopedTools projectScopedTools;
    private final ChatLedgerService ledger;

    public DigitalHumanAgentService(ChatModel chatModel,
                                    ToolRegistry toolRegistry,
                                    DigitalHumanAgentProperties properties,
                                    ProjectScopedTools projectScopedTools,
                                    ChatLedgerService ledger) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.projectScopedTools = projectScopedTools;
        this.ledger = ledger;
    }

    public AgentAnswer answer(Long projectId, String sessionId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        // 换了主脑，账本不能换：第 5 掌定下「Memory 是投影、chat_message 是事实」，
        // Agent 这条路同样要落账，否则运行页历史断档、session_stats 也永远查到 0 条。
        ConversationId conversationId = ConversationId.of(null, projectId, sessionId);
        ledger.append(projectId, conversationId, null, ChatMessage.Role.USER,
                question, ChatMessage.Status.COMPLETED);

        AgentTrace trace = new AgentTrace(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        AgentTrace.bind(trace);
        try {
            trace.event("agent.start");
            log.info("[agent] trace={} node=agent.start projectId={} sessionId={}",
                    trace.traceId(), projectId, sessionId);

            List<ToolCallback> tools = new ArrayList<>();
            tools.addAll(List.of(projectScopedTools.callbacksFor(projectId, sessionId, trace.traceId())));
            tools.addAll(List.of(toolRegistry.remote()));

            ReactAgent agent = ReactAgent.builder()
                    .name("digital_human_agent")
                    .model(chatModel)
                    .instruction(properties.instruction())
                    .tools(tools)
                    // 旁路身份：工具的审计留痕要能回答「谁、哪个项目、哪次追踪」。
                    // 不传的话审计行会丢掉 projectId 与 traceId，Agent 这一侧的工具调用就无法对账了。
                    .toolContext(toolContextOf(conversationId, trace.traceId()))
                    .toolExecutionExceptionProcessor(
                            DefaultToolExecutionExceptionProcessor.builder().alwaysThrow(false).build())
                    .hooks(List.of(ModelCallLimitHook.builder()
                            .runLimit(properties.modelCallLimit())
                            .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                            .build()))
                    .interceptors(List.of(
                            new AgentTracing.ModelTracing(),
                            new AgentTracing.ToolTracing()))
                    .build();

            AssistantMessage message;
            try {
                message = agent.call(question);
            } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException ex) {
                // 图运行失败：收敛成第 4 掌定下的模型错误语义，接口层才有确定的响应体
                throw new com.example.digitalhuman.ai.ModelInvocationException(
                        com.example.digitalhuman.ai.ModelInvocationException.Kind.PROVIDER_ERROR,
                        "agent", "digital_human_agent", "Agent 运行失败：" + ex.getMessage(), ex);
            }
            String text = message == null ? null : message.getText();
            trace.event("agent.end");

            String answer = (text == null || text.isBlank())
                    // 交付口径是「前端拿到一个字符串」（第 4 条验收）：空内容也要变成可展示的一句话
                    ? "抱歉，我没能得出可用的回答，请换个说法再问一次。"
                    : text;
            ledger.append(projectId, conversationId, null, ChatMessage.Role.ASSISTANT,
                    answer, ChatMessage.Status.COMPLETED);
            log.info("[agent] trace={} node=agent.end modelCalls={} events={}",
                    trace.traceId(), trace.modelCalls(), trace.events());
            return new AgentAnswer(answer, trace.traceId(), trace.modelCalls(), trace.events());
        } catch (RuntimeException ex) {
            trace.event("agent.error");
            // 失败也留档：历史里看不到「为什么这条没有下文」，排查就只能靠猜（第 5 掌的规矩）
            ledger.append(projectId, conversationId, null, ChatMessage.Role.ASSISTANT,
                    "Agent 运行失败：" + ex.getMessage(), ChatMessage.Status.FAILED);
            log.warn("[agent] trace={} node=agent.error type={} message={}",
                    trace.traceId(), ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        } finally {
            AgentTrace.unbind();
        }
    }

    /**
     * 旁路身份（模型看不见、也填不了）。
     *
     * <p>为什么不用 userId：Agent 这条路是只读的（写工具不注册给模型），
     * 所以这里刻意不塞一个假用户号——假身份一旦进了审计表，比空着更难查。
     * 写操作仍然只有第 6 掌那条「登录身份 + 一次性确认令牌」的路。
     */
    private static Map<String, Object> toolContextOf(ConversationId conversationId, String traceId) {
        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.PROJECT_ID, projectIdOf(conversationId));
        context.put(ToolContextKeys.SESSION_ID, conversationId.sessionId());
        context.put(ToolContextKeys.CONVERSATION_ID, conversationId.value());
        context.put(ToolContextKeys.TRACE_ID, traceId);
        return context;
    }

    /** conversationId = userId:projectId:sessionId，这里只取 projectId 那一段。 */
    private static Long projectIdOf(ConversationId conversationId) {
        String[] parts = conversationId.value().split(":");
        return parts.length == 3 ? Long.valueOf(parts[1]) : null;
    }

    /**
     * @param modelCalls 本次请求实际发生的模型调用次数（用来验证硬上界）
     * @param events     节点事件序列：agent.start → model#n → tool:xxx → agent.end
     */
    public record AgentAnswer(String reply, String traceId, int modelCalls, List<String> events) {
    }
}
