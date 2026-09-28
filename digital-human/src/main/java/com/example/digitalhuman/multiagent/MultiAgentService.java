package com.example.digitalhuman.multiagent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.example.digitalhuman.agent.ProjectScopedTools;
import com.example.digitalhuman.ai.FixedOptionsChatModel;
import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.tools.ToolContextKeys;
import com.example.digitalhuman.tools.ToolRegistry;

/**
 * 多 Agent 协作：一个接待、一个知识、一个业务，各自带自己的提示词、工具与记忆。
 *
 * <p>控制权的划分（这一掌真正要传达的东西）：
 * <ul>
 *   <li><b>Router</b> 决定「第一跳去哪」——一次轻量分类，落在三个角色之一；</li>
 *   <li><b>角色</b>自己决定「要不要交接」——在回复末尾输出 {@code handoff=<role>}；</li>
 *   <li><b>Supervisor</b> 决定「还要不要继续跳」——握着 {@code maxHops} 这个硬上界。</li>
 * </ul>
 * 三者叠起来，就得到了「局部灵活 + 全局有界」：角色可以自主交接，但跳不出预算。
 *
 * <p><b>为什么必须多 Agent，而不是一个 Agent 加更多工具</b>（验收第三条）：
 * 一次调用的上下文 = 系统提示词 + 全部工具的名称与 Schema + 历史消息 + 检索片段 + 本轮输入。
 * 工具从 4 个涨到 12 个、提示词随职责线性变长，这些成本**每轮都要付，且与问题是否相关无关**。
 * 拆开之后每个角色只带自己那份：候选空间小了一半以上，误选概率下降——
 * 能力上限没提高，是**上下文变窄**带来的确定性。判据只有一条：
 * 当不同职责对「提示词 / 工具集 / 记忆」的要求互相排斥时，才轮到多 Agent；只是工具多，加工具就够了。
 */
@Service
public class MultiAgentService {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentService.class);
    private static final Pattern HANDOFF = Pattern.compile("handoff\\s*=\\s*([a-zA-Z_]+)");

    private final ChatModel chatModel;
    private final ProjectScopedTools projectScopedTools;
    private final ToolRegistry toolRegistry;
    private final MultiAgentProperties properties;
    private final ChatLedgerService ledger;

    /** 三个角色各一份记忆：会话 ID 相同、命名空间不同，互不可见。 */
    private final Map<String, ChatMemory> memoriesByRole = new LinkedHashMap<>();

    public MultiAgentService(ChatModel chatModel,
                             ProjectScopedTools projectScopedTools,
                             ToolRegistry toolRegistry,
                             MultiAgentProperties properties,
                             ChatLedgerService ledger) {
        this.chatModel = chatModel;
        this.projectScopedTools = projectScopedTools;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.ledger = ledger;
        for (String role : MultiAgentRoles.ROUTABLE) {
            memoriesByRole.put(role, MessageWindowChatMemory.builder().maxMessages(20).build());
        }
    }

    /**
     * 一次多 Agent 会话。
     *
     * @param hops 本轮实际走过的角色序列（第一跳来自 Router，后续来自 handoff）
     */
    public record MultiAgentAnswer(String reply, String route, List<String> hops,
                                   Map<String, Integer> memorySizes, Map<String, List<String>> roleTools,
                                   String traceId) {
    }

    public MultiAgentAnswer ask(Long projectId, String sessionId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        ConversationId conversationId = ConversationId.of(null, projectId, sessionId);
        ledger.append(projectId, conversationId, null, ChatMessage.Role.USER, question,
                ChatMessage.Status.COMPLETED);

        List<String> hops = new ArrayList<>();
        try {
            // 第一跳由 Router 决定（一次分类，不给采样自由度）
            String role = route(question);
            String reply = "";
            for (int hop = 0; hop < properties.maxHops(); hop++) {
                hops.add(role);
                reply = invokeRole(projectId, sessionId, role, question);
                String next = handoffOf(reply);
                if (next == null || hops.contains(next)) {
                    break;
                }
                log.info("[multi-agent] hop={} {} → handoff={}", hop + 1, role, next);
                role = next;
            }
            String clean = stripHandoff(reply);
            ledger.append(projectId, conversationId, null, ChatMessage.Role.ASSISTANT, clean,
                    ChatMessage.Status.COMPLETED);
            log.info("[multi-agent] route={} hops={}", hops.get(0), hops);
            return new MultiAgentAnswer(clean, hops.get(0), hops, memorySizes(sessionId),
                    MultiAgentRoles.allTools(), conversationId.value());
        } catch (RuntimeException ex) {
            ledger.append(projectId, conversationId, null, ChatMessage.Role.ASSISTANT,
                    "多 Agent 运行失败：" + ex.getMessage(), ChatMessage.Status.FAILED);
            throw new ModelInvocationException(ModelInvocationException.Kind.PROVIDER_ERROR,
                    "multi-agent", "router", "多 Agent 运行失败：" + ex.getMessage(), ex);
        }
    }

    /** Router：一次分类调用（固定温度），结果必须落在白名单里，否则收敛到接待。 */
    private String route(String question) {
        ChatModel classifier = new FixedOptionsChatModel(chatModel,
                ChatOptions.builder().temperature(0.0).build());
        String answer = classifier.call(new Prompt(List.of(
                new SystemMessage("""
                        你是分流节点。只输出一个角色名，不要解释、不要标点：
                        reception（寒暄、身份确认、需求澄清、项目元信息）
                        knowledge（项目资料、制度、政策、文档内容）
                        business（实时数据：会话统计、展厅预约余位）
                        """),
                new UserMessage(question)))).getResult().getOutput().getText();
        String role = answer == null ? "" : answer.trim().toLowerCase();
        for (String candidate : MultiAgentRoles.ROUTABLE) {
            if (role.contains(candidate)) {
                log.info("[multi-agent] router={} raw={}", candidate, answer);
                return candidate;
            }
        }
        log.info("[multi-agent] router 未命中白名单，收敛到接待 raw={}", answer);
        return MultiAgentRoles.RECEPTION;
    }

    /** 一个角色的一次执行：自己的提示词（+ 按需注入的能力说明）、自己的工具、自己的记忆。 */
    private String invokeRole(Long projectId, String sessionId, String role, String question) {
        String namespace = MultiAgentRoles.memoryNamespace(role, sessionId);
        ChatMemory memory = memoriesByRole.get(role);
        List<Message> history = memory.get(namespace);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(properties.instructionOf(role) + properties.skillsFor(role)));
        messages.addAll(history);
        messages.add(new UserMessage(question));

        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(properties.instructionOf(role) + properties.skillsFor(role))
                .defaultToolCallbacks(toolsFor(projectId, sessionId, role))
                .build();
        String reply = client.prompt().messages(messages.subList(1, messages.size())).call().content();
        String text = reply == null ? "" : reply;

        // 记忆只写自己这一段：别的角色看不到
        memory.add(namespace, List.of(new UserMessage(question), new AssistantMessage(text)));
        return text;
    }

    /** 工具边界：只把这个角色名下的工具交出去（`project_info` / `knowledge_search` / 业务工具）。 */
    private ToolCallback[] toolsFor(Long projectId, String sessionId, String role) {
        List<String> allowed = MultiAgentRoles.toolsOf(role);
        List<ToolCallback> tools = new ArrayList<>();
        if (allowed.contains("project_info") || allowed.contains("knowledge_search")
                || allowed.contains("session_stats")) {
            List<String> scoped = allowed.stream()
                    .filter(name -> !"showroom_query_availability".equals(name))
                    .toList();
            if (!scoped.isEmpty()) {
                tools.addAll(List.of(projectScopedTools.callbacksFor(projectId, sessionId,
                        role + "-" + sessionId, scoped.toArray(String[]::new))));
            }
        }
        if (allowed.contains("showroom_query_availability")) {
            for (ToolCallback remote : toolRegistry.remote()) {
                if (allowed.contains(remote.getToolDefinition().name())) {
                    tools.add(remote);
                }
            }
        }
        return tools.toArray(ToolCallback[]::new);
    }

    private static String handoffOf(String reply) {
        Matcher matcher = HANDOFF.matcher(reply == null ? "" : reply);
        if (!matcher.find()) {
            return null;
        }
        String target = matcher.group(1).toLowerCase();
        return MultiAgentRoles.isRoutable(target) ? target : null;
    }

    private static String stripHandoff(String reply) {
        return reply == null ? "" : HANDOFF.matcher(reply).replaceAll("").trim();
    }

    /** 各角色记忆的条数：用来证明「互不污染」。 */
    public Map<String, Integer> memorySizes(String sessionId) {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        memoriesByRole.forEach((role, memory) ->
                sizes.put(role, memory.get(MultiAgentRoles.memoryNamespace(role, sessionId)).size()));
        return sizes;
    }

    /** 供测试与排查：某个角色在当前会话里看到的记忆条数。 */
    public Optional<ChatMemory> memoryOf(String role) {
        return Optional.ofNullable(memoriesByRole.get(role));
    }

    /** 工具边界自查：任何两个角色都不共享工具——这是硬约束，写成了可断言的方法。 */
    public static boolean toolsAreDisjoint() {
        Map<String, List<String>> all = MultiAgentRoles.allTools();
        Map<String, String> owner = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : all.entrySet()) {
            for (String tool : entry.getValue()) {
                if (owner.put(tool, entry.getKey()) != null) {
                    return false;
                }
            }
        }
        return true;
    }
}
