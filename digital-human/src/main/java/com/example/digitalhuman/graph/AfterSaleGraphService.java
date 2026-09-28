package com.example.digitalhuman.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.example.digitalhuman.agent.ProjectScopedTools;
import com.example.digitalhuman.ai.FixedOptionsChatModel;
import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.service.ProjectKnowledgeRetriever;
import com.example.digitalhuman.tools.ToolContextKeys;
import com.example.digitalhuman.tools.ToolRegistry;
import com.example.digitalhuman.workflow.WorkflowTrace;

/**
 * 售后流程的状态图：把「执行顺序 + 状态归属 + 人机边界」三件事分开。
 *
 * <pre>
 * START → intent → (knowledge ∥ biz) → risk →┬→ humanReview → reply → END
 *                                            └──────────────→ reply
 * </pre>
 *
 * <p>四件事在这里各归其位：
 * <ul>
 *   <li><b>执行顺序</b>写在边里（并行汇合、条件边）；</li>
 *   <li><b>状态归属</b>写在 {@link AfterSaleState#strategies()} 里（谁追加、谁覆盖）；</li>
 *   <li><b>人机边界</b>是一个节点（{@code humanReview}）+ {@code interruptBefore}，
 *       而不是一个阻塞的 if；</li>
 *   <li><b>断点</b>落在 {@link MysqlCheckpointSaver} 里，进程重启后还能接着跑。</li>
 * </ul>
 *
 * <p><b>为什么按请求建图</b>：与前两掌同因——工具要闭包项目身份。
 * 另外图本身是「结构」，重启后重建出来的是同一张图，检查点是按 threadId 索引的，
 * 所以「重建图 + 老检查点」能恢复（图结构版本化是遗留问题，写在验收记录里）。
 */
@Service
public class AfterSaleGraphService {

    private static final Logger log = LoggerFactory.getLogger(AfterSaleGraphService.class);
    private static final Pattern AMOUNT = Pattern.compile("(?:金额|amount)[^0-9]{0,4}([0-9]+(?:\\.[0-9]+)?)");

    private final ChatModel chatModel;
    private final ChatModel classificationModel;
    private final ProjectKnowledgeRetriever retriever;
    private final ProjectScopedTools projectScopedTools;
    private final ToolRegistry toolRegistry;
    private final AfterSaleProperties properties;
    private final MysqlCheckpointSaver checkpointSaver;
    private final ChatLedgerService ledger;

    public AfterSaleGraphService(ChatModel chatModel,
                                 ProjectKnowledgeRetriever retriever,
                                 ProjectScopedTools projectScopedTools,
                                 ToolRegistry toolRegistry,
                                 AfterSaleProperties properties,
                                 MysqlCheckpointSaver checkpointSaver,
                                 ChatLedgerService ledger) {
        this.chatModel = chatModel;
        // 分类要被条件边当条件用：固定温度，不给它采样自由度（第 10 掌的教训）
        this.classificationModel = new FixedOptionsChatModel(chatModel,
                ChatOptions.builder().temperature(0.0).build());
        this.retriever = retriever;
        this.projectScopedTools = projectScopedTools;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.checkpointSaver = checkpointSaver;
        this.ledger = ledger;
    }

    /**
     * 一次图运行的结果。
     *
     * @param status        {@code INTERRUPTED}（停在人工确认前）/ {@code DONE}
     * @param executedNodes 本次真正执行过的节点（恢复时这里只有断点之后的节点——这就是「不重跑」的证据）
     */
    public record GraphRun(String threadId, String status, List<String> executedNodes,
                           Map<String, String> state, String reply) {
    }

    /** 第一次进入：跑到断点（高风险）或跑完（低风险）。 */
    public GraphRun run(Long projectId, String sessionId, String question, String threadId) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        String resolvedThreadId = threadId == null || threadId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : threadId;

        ConversationId conversationId = ConversationId.of(null, projectId, sessionId);
        ledger.append(projectId, conversationId, null, ChatMessage.Role.USER, question,
                ChatMessage.Status.COMPLETED);

        WorkflowTrace trace = new WorkflowTrace(resolvedThreadId, "after-sale");
        try {
            CompiledGraph graph = buildGraph(projectId, sessionId, resolvedThreadId);
            RunnableConfig config = RunnableConfig.builder()
                    // threadId 是运行时配置、不是业务状态：检查点就按它索引，混进状态里恢复会失败
                    .threadId(resolvedThreadId)
                    .build();
            streamToState(graph, Map.of(AfterSaleState.INPUT, question), config, trace);
            return finishRun(graph, config, resolvedThreadId, trace, projectId, conversationId);
        } catch (GraphStateException ex) {
            ledger.append(projectId, conversationId, null, ChatMessage.Role.ASSISTANT,
                    "图运行失败：" + ex.getMessage(), ChatMessage.Status.FAILED);
            throw new ModelInvocationException(ModelInvocationException.Kind.PROVIDER_ERROR,
                    "graph", "after-sale", "图运行失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 人工确认之后继续：**用同一个 threadId**，从 {@code humanReview} 之后接着跑。
     *
     * <p>恢复的关键不是「再调一次」，而是「索引键一致」：检查点按 {@code RunnableConfig.threadId} 存，
     * 两次调用必须是同一个值，否则表现就是「参数明明对，恢复就是重跑」。
     */
    public GraphRun resume(Long projectId, String sessionId, String threadId, String decision) {
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("threadId 不能为空");
        }
        String normalized = "APPROVE".equalsIgnoreCase(decision) ? "APPROVE" : "REJECT";
        ConversationId conversationId = ConversationId.of(null, projectId, sessionId);

        WorkflowTrace trace = new WorkflowTrace(threadId, "after-sale-resume");
        try {
            CompiledGraph graph = buildGraph(projectId, sessionId, threadId);
            // 恢复不是「再调一次」：RunnableConfig 里必须显式说明这是恢复，
            // 并指向要接着跑的那个检查点。只给 threadId 的话框架会当成一次全新的运行，
            // 表现就是「参数明明对，恢复却从头重跑」（第一版就是这么错的）
            List<MysqlCheckpointSaver.CheckpointRecord> history = checkpointSaver.history(threadId);
            RunnableConfig.Builder configBuilder = RunnableConfig.builder()
                    .threadId(threadId)
                    .resume();
            if (!history.isEmpty()) {
                configBuilder.checkPointId(history.get(history.size() - 1).checkpointId());
            }
            RunnableConfig config = configBuilder.build();
            // 光有 resume() 还不够：图的 CompileConfig 里写死了「进入 humanReview 之前必须停」，
            // 所以恢复时必须显式告诉运行时「这个节点这次放行」，否则会原地再停一次
            // （实测表现：resume 之后状态还是 INTERRUPTED）
            config.withNodeResumed(AfterSaleState.NODE_HUMAN_REVIEW);
            streamToState(graph, Map.of(AfterSaleState.HUMAN_DECISION, normalized), config, trace);
            return finishRun(graph, config, threadId, trace, projectId, conversationId);
        } catch (GraphStateException ex) {
            throw new ModelInvocationException(ModelInvocationException.Kind.PROVIDER_ERROR,
                    "graph", "after-sale", "恢复失败：" + ex.getMessage(), ex);
        }
    }

    /** 「这条工单现在卡在哪一步」——检查点历史就是答案。 */
    public List<MysqlCheckpointSaver.CheckpointRecord> history(String threadId) {
        return checkpointSaver.history(threadId);
    }

    /**
     * 把图导出成 Mermaid：**流程不用读代码就能看懂**（验收第一条）。
     *
     * <p>结构由框架从真实的节点与边算出来，不是我们手画的——手画的图迟早和代码漂移，
     * 而这份导出改了边就跟着变。
     */
    public String asMermaid(Long projectId, String sessionId) {
        try {
            return assembleGraph(projectId, sessionId, "preview")
                    .getGraph(com.alibaba.cloud.ai.graph.GraphRepresentation.Type.MERMAID, "after-sale")
                    .content();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("导出图结构失败：" + ex.getMessage(), ex);
        }
    }

    // ---- 图的装配 ----

    private CompiledGraph buildGraph(Long projectId, String sessionId, String threadId)
            throws GraphStateException {
        return assembleGraph(projectId, sessionId, threadId).compile(compileConfig());
    }

    private CompileConfig compileConfig() {
        return CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
                // 断点画在 humanReview **之前**：写成 interruptAfter 的话，
                // 人工节点里的任何副作用都会先执行再暂停，恢复时可能重复执行
                .interruptBefore(AfterSaleState.NODE_HUMAN_REVIEW)
                .build();
    }

    private StateGraph assembleGraph(Long projectId, String sessionId, String threadId)
            throws GraphStateException {
        StateGraph graph = new StateGraph("after-sale", AfterSaleState.strategies());

        graph.addNode(AfterSaleState.NODE_INTENT, AsyncNodeAction.node_async(this::judgeIntent));

        graph.addNode(AfterSaleState.NODE_KNOWLEDGE,
                AsyncNodeAction.node_async(state -> searchKnowledge(projectId, state)));

        // 业务查询不是一次调用：查预约余位、查会话记录都可能要多轮工具调用。
        // 与其在节点里手写循环，不如把这一格交给 Agent Framework（文章 V2 的第三处修改）
        graph.addNode(AfterSaleState.NODE_BIZ, bizAgent(projectId, sessionId, threadId).asNode());

        graph.addNode(AfterSaleState.NODE_RISK, AsyncNodeAction.node_async(this::assessRisk));

        graph.addNode(AfterSaleState.NODE_HUMAN_REVIEW, AsyncNodeAction.node_async(state ->
                Map.of(AfterSaleState.HUMAN_DECISION,
                        String.valueOf(state.value(AfterSaleState.HUMAN_DECISION).orElse("PENDING")),
                        AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_HUMAN_REVIEW))));

        graph.addNode(AfterSaleState.NODE_REPLY, AsyncNodeAction.node_async(this::composeReply));

        graph.addEdge(StateGraph.START, AfterSaleState.NODE_INTENT);
        graph.addEdge(AfterSaleState.NODE_INTENT, AfterSaleState.NODE_KNOWLEDGE);
        graph.addEdge(AfterSaleState.NODE_INTENT, AfterSaleState.NODE_BIZ);
        graph.addEdge(List.of(AfterSaleState.NODE_KNOWLEDGE, AfterSaleState.NODE_BIZ),
                AfterSaleState.NODE_RISK);
        graph.addConditionalEdges(AfterSaleState.NODE_RISK,
                AsyncEdgeAction.edge_async(state -> isHighRisk(state)
                        ? AfterSaleState.NODE_HUMAN_REVIEW : AfterSaleState.NODE_REPLY),
                Map.of(AfterSaleState.NODE_HUMAN_REVIEW, AfterSaleState.NODE_HUMAN_REVIEW,
                        AfterSaleState.NODE_REPLY, AfterSaleState.NODE_REPLY));
        graph.addEdge(AfterSaleState.NODE_HUMAN_REVIEW, AfterSaleState.NODE_REPLY);
        graph.addEdge(AfterSaleState.NODE_REPLY, StateGraph.END);
        return graph;
    }

    private ReactAgent bizAgent(Long projectId, String sessionId, String threadId) {
        List<ToolCallback> tools = new ArrayList<>(
                List.of(projectScopedTools.callbacksFor(projectId, sessionId, threadId,
                        "session_stats", "project_info")));
        tools.addAll(List.of(toolRegistry.remote()));

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ToolContextKeys.PROJECT_ID, projectId);
        toolContext.put(ToolContextKeys.CONVERSATION_ID,
                ConversationId.of(null, projectId, sessionId).value());

        return ReactAgent.builder()
                .name("biz")
                .model(chatModel)
                .instruction("""
                        你是业务查询节点。按用户诉求调用业务工具（展厅预约余位、会话统计），
                        只返回结构化的业务事实，不要解释、不要给建议。
                        查不到就明确写「查不到」，不要猜。
                        """)
                .tools(tools)
                .toolContext(toolContext)
                // 刻意**不设 outputKey**：Agent 节点写自定义 key 时，落进状态的是一个
                // GraphResponse 包装对象，下游拿到它等于什么都没拿到（真实验收时从响应里看出来的）。
                // 走框架默认路径（写 messages）反而干净——这也正是文章那句
                // 「子 Agent 之间的输出契约要明确定义，否则上游的自由文本会污染下游的输入」的含义。
                .build();
    }

    // ---- 节点实现 ----

    /** 意图分类：LLM 只做一次分类，输出会被条件边当条件用。 */
    private Map<String, Object> judgeIntent(OverAllState state) throws Exception {
        String question = String.valueOf(state.value(AfterSaleState.INPUT).orElse(""));
        String answer = classificationModel.call(new Prompt(
                List.of(new org.springframework.ai.chat.messages.SystemMessage(properties.intentInstruction()),
                        new UserMessage(question)))).getResult().getOutput().getText();
        String label = normalizeLabel(answer);
        log.info("[graph] node=intent label={} raw={}", label, answer);
        return Map.of(AfterSaleState.INTENT, label, AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_INTENT));
    }

    /** 分类结果必须落进白名单：模型写别的（带解释、带标点、写中文）都收敛成 OTHER，条件边才不会跑飞。 */
    private static String normalizeLabel(String answer) {
        if (answer == null) {
            return "OTHER";
        }
        String upper = answer.trim().toUpperCase();
        for (String label : List.of("REFUND", "REPAIR", "RESCHEDULE", "VISIT")) {
            if (upper.contains(label)) {
                return label;
            }
        }
        return "OTHER";
    }

    private Map<String, Object> searchKnowledge(Long projectId, OverAllState state) throws Exception {
        String question = String.valueOf(state.value(AfterSaleState.INPUT).orElse(""));
        List<Document> hits = retriever.retrieve(projectId, question);
        List<String> rendered = hits.stream()
                .map(hit -> "来源=" + hit.getMetadata().get("docName") + "#" + hit.getMetadata().get("chunkIndex")
                        + " " + hit.getText())
                .toList();
        log.info("[graph] node=knowledge projectId={} hits={}", projectId, rendered.size());
        // 注意这里**不返回空 Map**：返回空 Map 的节点不会产生 NodeOutput，
        // 于是节点埋点里看不到它——而「该查的没查」恰恰是要靠节点序列来判断的（实测踩到）
        if (rendered.isEmpty()) {
            return Map.of(AfterSaleState.KNOWLEDGE_HITS, List.of("（知识库没有相关片段：已查询，0 条命中）"),
                    AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_KNOWLEDGE));
        }
        return Map.of(AfterSaleState.KNOWLEDGE_HITS, rendered,
                AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_KNOWLEDGE));
    }

    private Map<String, Object> assessRisk(OverAllState state) throws Exception {
        String intent = String.valueOf(state.value(AfterSaleState.INTENT).orElse("OTHER"));
        List<String> records = List.of(bizRecordsText(state));
        double amount = extractAmount(records);
        boolean high = properties.humanReviewIntents().contains(intent)
                || (amount > 0 && amount > properties.riskAmountThreshold());
        String level = high ? "HIGH" : "LOW";
        log.info("[graph] node=risk intent={} amount={} threshold={} level={}",
                intent, amount, properties.riskAmountThreshold(), level);
        return Map.of(AfterSaleState.RISK_LEVEL, level, AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_RISK));
    }

    private static boolean isHighRisk(OverAllState state) {
        return "HIGH".equals(String.valueOf(state.value(AfterSaleState.RISK_LEVEL).orElse("LOW")));
    }

    private static double extractAmount(List<String> records) {
        for (String record : records) {
            Matcher matcher = AMOUNT.matcher(record);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }
        return 0d;
    }

    /**
     * 从状态里取「业务记录」的可读文本。
     *
     * <p>这一步之所以需要，是因为 **Agent 节点在状态里留下的不是字符串**：
     * 实测它是 {@link GraphResponse} / {@code NodeOutput} / {@code Message} / {@code List} 的嵌套，
     * 直接 `String.valueOf` 得到的是 `com.alibaba.cloud.ai.graph.GraphResponse@121087b1` 这种对象地址。
     * 下游节点拿到对象地址，等于什么都没拿到——这正是文章说的
     * 「子 Agent 之间的输出契约要明确定义，否则上游的产出会污染下游的输入」。
     * 所以这里显式定义契约：**业务记录 = 状态里最后一段可读的助手文本**。
     */
    private static String bizRecordsText(OverAllState state) {
        String fromKey = textOf(state.value(AfterSaleState.BIZ_RECORDS).orElse(null));
        if (fromKey != null && !fromKey.isBlank()) {
            return fromKey;
        }
        String fromMessages = textOf(state.value(AfterSaleState.MESSAGES).orElse(null));
        return fromMessages == null || fromMessages.isBlank() ? "（这次没有取到业务记录）" : fromMessages;
    }

    /** 把状态里的值尽量读成文本：GraphResponse / NodeOutput / OverAllState / Message / List / String 任意嵌套。 */
    private static String textOf(Object value) {
        Object current = value;
        for (int depth = 0; depth < 5; depth++) {
            if (current == null) {
                return null;
            }
            if (current instanceof Message message) {
                return message.getText();
            }
            if (current instanceof String text) {
                return text;
            }
            if (current instanceof GraphResponse<?> response) {
                if (response.getOutput() == null) {
                    return null;
                }
                try {
                    current = response.getOutput().join();
                } catch (RuntimeException ex) {
                    return null;
                }
                continue;
            }
            if (current instanceof NodeOutput output) {
                current = output.state();
                continue;
            }
            if (current instanceof OverAllState inner) {
                Object innerMessages = inner.value(AfterSaleState.MESSAGES).orElse(null);
                if (innerMessages == null || innerMessages == current) {
                    return null;
                }
                current = innerMessages;
                continue;
            }
            if (current instanceof List<?> list) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    String text = textOf(list.get(i));
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
                return null;
            }
            return String.valueOf(current);
        }
        return null;
    }

    /** 答复节点：生成类节点，保留采样自由度（与分类节点形成对照）。 */
    private Map<String, Object> composeReply(OverAllState state) throws Exception {
        String decision = String.valueOf(state.value(AfterSaleState.HUMAN_DECISION).orElse(""));
        if ("REJECT".equals(decision)) {
            return Map.of(AfterSaleState.REPLY, "这个申请需要人工重新评估，我已经把工单转给售后专员，请留意后续联系。",
                AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_REPLY));
        }
        String material = """
                用户问题：%s
                意图：%s
                风险等级：%s
                人工决定：%s
                知识库片段：%s
                业务记录：%s
                """.formatted(
                state.value(AfterSaleState.INPUT).orElse(""),
                state.value(AfterSaleState.INTENT).orElse("OTHER"),
                state.value(AfterSaleState.RISK_LEVEL).orElse("LOW"),
                decision.isBlank() ? "无需人工" : decision,
                String.join(" | ", AfterSaleState.asTextList(
                        state.value(AfterSaleState.KNOWLEDGE_HITS).orElse(null))),
                bizRecordsText(state));

        String reply = chatModel.call(new Prompt(List.of(
                new org.springframework.ai.chat.messages.SystemMessage(properties.replyInstruction()),
                new UserMessage(material)))).getResult().getOutput().getText();
        log.info("[graph] node=reply length={}", reply == null ? 0 : reply.length());
        return Map.of(AfterSaleState.REPLY, reply == null ? "" : reply,
                AfterSaleState.NODE_LOG, List.of(AfterSaleState.NODE_REPLY));
    }

    // ---- 运行与收尾 ----

    private void streamToState(CompiledGraph graph, Map<String, Object> input, RunnableConfig config,
                               WorkflowTrace trace) {
        long startedAt = System.nanoTime();
        graph.stream(input, config).doOnNext((NodeOutput output) -> {
            String node = displayName(output);
            if (node == null) {
                return;
            }
            trace.observe(node, (System.nanoTime() - startedAt) / 1_000_000);
        }).blockLast();
    }

    /**
     * 节点名：优先用 {@code NodeOutput.agent()}（形如 {@code subgraph_biz}），去掉 {@code subgraph_} 前缀。
     *
     * <p>为什么不用 {@code node()}：Agent 节点在图上是一个子图，它吐出来的 node 是内部 id
     * （{@code _AGENT_MODEL_}、{@code _AGENT_HOOK_InstructionAgentHook.before}、{@code __END__}），
     * 按它记埋点，节点序列就变成了一串内部实现细节（第 10 掌踩过同一个坑）。
     */
    private static String displayName(NodeOutput output) {
        String name = output.agent();
        if (name == null || name.isBlank()) {
            name = output.node();
        }
        if (name == null || name.isBlank()
                || "START".equalsIgnoreCase(name) || "__START__".equals(name)
                || "END".equalsIgnoreCase(name) || "__END__".equals(name)) {
            return null;
        }
        return name.startsWith("subgraph_") ? name.substring("subgraph_".length()) : name;
    }

    private GraphRun finishRun(CompiledGraph graph, RunnableConfig config, String threadId,
                               WorkflowTrace trace, Long projectId, ConversationId conversationId) {
        // 读「现在到哪一步了」要单开一个只带 threadId 的 config：
        // 恢复时用的 config 上带着**旧**的 checkPointId，拿它去 getState 只会看到旧快照，
        // 于是明明已经跑完了，状态还报 INTERRUPTED（实测踩到）
        RunnableConfig statusConfig = RunnableConfig.builder().threadId(threadId).build();
        StateSnapshot snapshot = graph.getState(statusConfig);
        String next = snapshot == null ? null : snapshot.next();
        // 判断「是否中断」不能只看 next 非空：跑完时 next 也会是 __END__（实测踩到，
        // 结果就是每次正常结束都被报成 INTERRUPTED）
        boolean interrupted = next != null && !next.isBlank()
                && !"__END__".equals(next) && !"END".equalsIgnoreCase(next);
        OverAllState state = snapshot == null ? null : snapshot.state();

        Map<String, String> visible = new java.util.LinkedHashMap<>();
        if (state != null) {
            for (String key : List.of(AfterSaleState.INTENT, AfterSaleState.KNOWLEDGE_HITS,
                    AfterSaleState.BIZ_RECORDS, AfterSaleState.RISK_LEVEL,
                    AfterSaleState.HUMAN_DECISION, AfterSaleState.REPLY, AfterSaleState.NODE_LOG)) {
                state.value(key).ifPresent(value -> visible.put(key,
                        AfterSaleState.BIZ_RECORDS.equals(key) ? bizRecordsText(state) : summarize(value)));
            }
        }
        String reply = visible.getOrDefault(AfterSaleState.REPLY, "");
        // 业务记录统一用同一个 key 对外暴露：业务节点走 messages，但调用方不该关心它落在哪
        visible.putIfAbsent(AfterSaleState.BIZ_RECORDS, bizRecordsText(state));
        String status = interrupted ? "INTERRUPTED" : "DONE";

        // 断点状态也要进账本：否则「历史里一条没有回复的提问，没人知道为什么」——第 5 掌的规矩
        ledger.append(projectId, conversationId, null, ChatMessage.Role.ASSISTANT,
                interrupted ? "[已转人工确认，等待处理] " + next : reply,
                interrupted ? ChatMessage.Status.PENDING : ChatMessage.Status.COMPLETED);

        log.info("[graph] thread={} status={} next={} executed={}", threadId, status, next,
                trace.nodeNames());
        return new GraphRun(threadId, status, trace.nodeNames(), visible, reply);
    }

    private static String summarize(Object value) {
        String text = String.join(" | ", AfterSaleState.asTextList(value));
        return text.length() <= 400 ? text : text.substring(0, 400) + "…";
    }
}
