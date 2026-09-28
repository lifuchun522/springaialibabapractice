package com.example.digitalhuman.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.FlowAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LoopAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.loop.LoopMode;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.example.digitalhuman.agent.ProjectScopedTools;
import com.example.digitalhuman.ai.FixedOptionsChatModel;
import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.tools.ToolRegistry;

/**
 * 编排层：把「谁在什么条件下跑」写进 Java，把「内容怎么生成」留给模型。
 *
 * <p>四种形态各管一件事（第 10 掌的主题）：
 * <ul>
 *   <li><b>顺序</b>管链路：understand → retrieve → answer，检索必须发生，跳过在结构上不存在；</li>
 *   <li><b>并行</b>管吞吐：知识库与业务系统同时查，两条分支写各自的 key，互不覆盖；</li>
 *   <li><b>路由</b>管分流：售前 / 售后 / 兜底，LLM 只做一次分类；</li>
 *   <li><b>循环</b>管完整：信息不足就追问，最多 {@code loopMaxRounds} 轮，超限转人工。</li>
 * </ul>
 *
 * <p><b>为什么按请求构建</b>：与第 9 掌同因——身份不能由模型填，工具对象要闭包项目与会话。
 * 另外这里还多一层用途：**按节点裁剪工具集合**（检索节点只拿到检索工具），
 * 让「这个节点不该干的事」在结构上做不到，而不是写在提示词里求模型配合。
 *
 * <p><b>节点埋点</b>：用 {@code stream(...)} 拿每个节点的输出，按节点名与耗时记入 {@link WorkflowTrace}。
 * 一次请求结束后，调用方能拿到「经过了哪些节点、各自多久」。
 */
@Service
public class DigitalHumanWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanWorkflowService.class);

    /** 流程图里的结束节点名，埋点时过滤掉，只留业务节点。 */
    private static final String END_NODE = "__END__";

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final ProjectScopedTools projectScopedTools;
    private final WorkflowProperties properties;
    private final ChatLedgerService ledger;

    public DigitalHumanWorkflowService(ChatModel chatModel,
                                       ToolRegistry toolRegistry,
                                       ProjectScopedTools projectScopedTools,
                                       WorkflowProperties properties,
                                       ChatLedgerService ledger) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.projectScopedTools = projectScopedTools;
        this.properties = properties;
        this.ledger = ledger;
    }

    /** 编排结果的对外形状：一个字符串 + 分支 + 节点时间线 + 节点序列 + 各节点写下的状态键。 */
    public record WorkflowAnswer(String reply, String mode, String branch, String traceId,
                                 List<WorkflowTrace.NodeTiming> nodes,
                                 List<String> sequence,
                                 Map<String, String> outputs) {
    }

    /** 我们关心的状态键：并行分支的独立产出、归并结果、各分支答案。 */
    private static final List<String> TRACKED_KEYS = List.of(
            "knowledge_hits", "biz_records", "merged_material",
            "presale_answer", "aftersale_answer", "fallback_answer",
            "completeness", "clarify");

    public WorkflowAnswer sequential(Long projectId, String sessionId, String question) {
        return run("sequential", projectId, sessionId, question, context -> {
            ReactAgent understand = node("understand", properties.understandInstruction(), context);
            ReactAgent retrieve = node("retrieve", properties.retrieveInstruction(), context,
                    context.knowledgeTools());
            ReactAgent answer = node("answer", properties.answerInstruction(), context);
            SequentialAgent chain = SequentialAgent.builder()
                    .name("qa-sequential")
                    .description("顺序流程：理解 → 检索 → 回答")
                    .subAgents(List.of(understand, retrieve, answer))
                    .build();
            return new FlowPlan((ctx, trace) -> invokeWithTracing(chain, question, trace), null);
        });
    }

    /**
     * 并行召回 + 显式归并。
     *
     * <p><b>为什么归并是代码节点，而不是再挂一个 Agent</b>（实测踩出来的）：
     * 分支节点用 {@code outputKey} 把产出写到自己的状态键上，**就不会再进 messages**，
     * 于是下游的 Agent 节点在提示词里看不到这两份材料（线上表现是模型回答
     * 「未收到上游两条分支的具体内容」）。让归并变成一段确定性代码——
     * 直接读两个键、按固定格式拼起来——既把「两份材料都在」变成结构保证，
     * 也顺手说明了一件事：**不是每个节点都必须由模型来跑**。
     *
     * <p>归并出的材料随后作为输入交给回答节点生成最终答复，调用方拿到的仍是一个字符串。
     */
    public WorkflowAnswer parallel(Long projectId, String sessionId, String question) {
        return run("parallel", projectId, sessionId, question, context -> {
            // 两条分支写各自的 key：合并结果里两个来源都要在（验收 2 的关键）
            ReactAgent knowledgeQuery = node("knowledge-query",
                    "你是知识库查询节点，只返回原文片段与来源标识，不要总结。", context,
                    context.knowledgeTools(), "knowledge_hits");
            ReactAgent bizQuery = node("biz-query",
                    "你是业务查询节点，只返回业务系统里的结构化字段（如预约余位、会话条数），不要解释。",
                    context, context.bizTools(), "biz_records");

            ParallelAgent recall = ParallelAgent.builder()
                    .name("parallel-recall")
                    .description("并行召回：知识库 + 业务系统，各写自己的状态键")
                    .subAgents(List.of(knowledgeQuery, bizQuery))
                    .maxConcurrency(2)
                    .build();

            ReactAgent answer = node("answer-with-material",
                    properties.answerInstruction(), context, null, "merged_answer");

            return new FlowPlan((ctx, trace) -> {
                // 第一步：并行召回（框架负责并发）
                RunOutcome recalled = invokeWithTracing(recall, question, trace);
                Map<String, String> branchOutputs = recalled.outputs();

                // 第二步：确定性归并（代码节点）
                long mergeStart = System.nanoTime();
                String merged = mergeBranchOutputs(branchOutputs);
                trace.node("merge", (System.nanoTime() - mergeStart) / 1_000_000);

                // 第三步：把归并材料作为输入，交给回答节点收口成一个字符串
                String material = """
                        用户问题：%s

                        以下是两条并行分支取回的材料（两边都要用到，哪边没有就说哪边没有）：
                        %s
                        """.formatted(question, merged);
                RunOutcome answered = invokeWithTracing(answer, material, trace);

                Map<String, String> outputs = new java.util.LinkedHashMap<>(branchOutputs);
                outputs.put("merged_material", merged);
                return new RunOutcome(answered.reply(), outputs);
            }, null);
        });
    }

    /**
     * 归并两条分支的产出：缺哪边就写哪边缺失。
     *
     * <p>刻意不做「智能」处理：拼接、标注来源、缺就写缺。归并属于控制流，
     * 控制流不该交给采样（这一掌的原话）。
     */
    static String mergeBranchOutputs(Map<String, String> branchOutputs) {
        String knowledge = branchOutputs.get("knowledge_hits");
        String biz = branchOutputs.get("biz_records");
        StringBuilder merged = new StringBuilder();
        merged.append("【知识库】\n")
                .append(knowledge == null || knowledge.isBlank() ? "（无命中）" : knowledge.trim())
                .append("\n\n【业务系统】\n")
                .append(biz == null || biz.isBlank() ? "（无记录）" : biz.trim());
        return merged.toString();
    }

    /**
     * 售前 / 售后 / 兜底路由。
     *
     * <p>关于兜底：框架的 {@code LlmRoutingAgent} 有 {@code fallbackAgent}，
     * 但对「与两类都无关」的问题，靠描述里显式声明一个 fallback 分支更可控——
     * 确定性流程最大的风险就是把不属于任何分支的问题硬塞进某个分支（文章 9.5）。
     */
    /**
     * 售前 / 售后 / 兜底路由。
     *
     * <p>这里有两件事必须说清楚（都是实测出来的，见 docs/ch10-验收记录.md）：
     * <ol>
     *   <li>框架的 {@code LlmRoutingAgent} 默认允许一次返回**多个**子 Agent 并行执行，
     *       所以判断口径里必须写死「只能返回一个」，否则「这次走了几条分支」不可复现；</li>
     *   <li>{@code fallbackAgent(...)} 并不覆盖「模型给了一个不在清单里的分支名」这种情况——
     *       实测是抛 {@code Failed to get valid decision after N retries}，
     *       于是「边界之外必须留出口」这件事得由我们自己接住：路由失败时直接跑兜底节点。</li>
     * </ol>
     */
    public WorkflowAnswer routed(Long projectId, String sessionId, String question) {
        return run("routed", projectId, sessionId, question, context -> {
            ReactAgent presale = node("presale",
                    """
                    你是售前咨询节点。只回答产品能力、价格、试用、选型、私有化部署、参观预约怎么联系这类问题，
                    需要事实时先检索知识库，不要承诺知识库里没有的服务条款。回答简短口语化。
                    """, context, context.knowledgeTools(), "presale_answer",
                    "售前咨询：产品能力、价格、试用、选型、私有化部署、参观/预约怎么问");
            ReactAgent aftersale = node("aftersale",
                    """
                    你是售后与业务查询节点。只处理已购产品的使用、故障、进度、预约余位、工单这类问题，
                    需要数据时必须调用业务工具（预约余位、会话统计），查不到就说查不到，不要猜。回答简短口语化。
                    """, context, context.bizTools(), "aftersale_answer",
                    "售后与业务查询：已购产品的使用、故障、进度、展厅预约余位、会话统计");
            ReactAgent fallback = node("fallback",
                    """
                    你是兜底节点。用户的问题不属于售前咨询，也不属于售后与业务查询。
                    直接说明你不负责这类问题，并告诉用户可以问什么，不要硬答。回答简短口语化。
                    """, context, context.allTools(), "fallback_answer",
                    "兜底：与售前、售后都无关的其它问题");

            Agent router = LlmRoutingAgent.builder()
                    .name("presale-aftersale-router")
                    // 路由分类调用固定参数：分类结果会被代码当条件用，不能有采样自由度。
                    // 生成类节点（下面的 presale/aftersale/fallback）仍然用原始 chatModel，保留生成自由度
                    .model(new FixedOptionsChatModel(chatModel,
                            ChatOptions.builder().temperature(properties.routeTemperature()).build()))
                    // 注意用的是 systemPrompt 而不是 description：
                    // 框架的 RoutingNode 读的是 systemPrompt/instruction 来拼判断提示词，
                    // 子 Agent 的 description 才是「可选分支清单」里给模型看的职责说明
                    .systemPrompt(properties.routeInstruction())
                    .description("售前 / 售后 / 兜底 路由（LLM 只做一次分类）")
                    .subAgents(List.of(presale, aftersale, fallback))
                    .fallbackAgent("fallback")
                    .build();
            return new FlowPlan((ctx, trace) -> invokeWithTracing(router, question, trace), fallback);
        });
    }

    /**
     * 补信息循环：完整性检查 → 追问，最多 {@code loopMaxRounds} 轮。
     *
     * <p>这里有个必须说清的边界：**同步 HTTP 请求里没有「用户的下一次回答」**。
     * 所以这个循环做的是「反复自检到不能再自检，然后产出一句追问」，
     * 真正的跨轮追问要等状态可持久化（第 11 掌的 checkpoint）。
     * 不假装它已经能跨轮——那需要状态，而状态现在还在内存里。
     */
    public WorkflowAnswer loop(Long projectId, String sessionId, String question) {
        return run("loop", projectId, sessionId, question, context -> {
            ReactAgent check = node("check-completeness",
                    """
                    你是完整性检查节点。看用户的问题里，回答所必需的信息是否齐备
                    （例：查预约余位需要展厅名与日期；问政策需要明确是哪个主题）。
                    只输出一行：信息齐备，或「缺失：<那一个最关键的缺失项>」。
                    """, context, null, "completeness");
            ReactAgent clarify = node("ask-back", properties.clarifyInstruction(), context, null, "clarify");

            // LoopAgent 只接受**一个** subAgent（框架会直接报错：
            // “LoopAgent must have only one subAgent, please use subAgent() method.”），
            // 所以「先检查再追问」这一对节点要先组合成一个顺序体，再交给循环——
            // 这也顺带说明四类 Flow Agent 是可以互相嵌套的，不是四个互斥的模板
            SequentialAgent checkThenAsk = SequentialAgent.builder()
                    .name("check-then-ask")
                    .description("循环体：检查完整度 → 必要时追问")
                    .subAgents(List.of(check, clarify))
                    .build();

            LoopAgent loop = LoopAgent.builder()
                    .name("info-completion-loop")
                    .description("信息补齐：校验完整度，缺失则追问，最多 " + properties.loopMaxRounds() + " 轮")
                    .subAgent(checkThenAsk)
                    .loopStrategy(LoopMode.count(properties.loopMaxRounds()))
                    .build();
            return new FlowPlan((ctx, trace) -> invokeWithTracing(loop, question, trace), null);
        });
    }

    // ---- 内部实现 ----

    /** 一次编排运行需要的上下文：身份 + 按节点裁剪工具。 */
    private final class RunContext {

        private final Long projectId;
        private final String sessionId;
        private final String traceId;
        private final Map<String, Object> toolContext = new HashMap<>();
        private final ToolCallback[] projectTools;
        private final ToolCallback[] remoteTools;

        RunContext(Long projectId, String sessionId, String traceId) {
            this.projectId = projectId;
            this.sessionId = sessionId;
            this.traceId = traceId;
            this.projectTools = projectScopedTools.callbacksFor(projectId, sessionId, traceId);
            this.remoteTools = toolRegistry.remote();
            toolContext.put(com.example.digitalhuman.tools.ToolContextKeys.PROJECT_ID, projectId);
            toolContext.put(com.example.digitalhuman.tools.ToolContextKeys.SESSION_ID,
                    ConversationId.normalizeSession(sessionId));
            toolContext.put(com.example.digitalhuman.tools.ToolContextKeys.CONVERSATION_ID,
                    ConversationId.of(null, projectId, sessionId).value());
            toolContext.put(com.example.digitalhuman.tools.ToolContextKeys.TRACE_ID, traceId);
        }

        ToolCallback[] knowledgeTools() {
            return pick(projectTools, "knowledge_search");
        }

        ToolCallback[] bizTools() {
            // 业务侧 = 远程 MCP 工具（展厅预约）+ 本地会话统计；两边都要留痕，所以都过审计包装
            List<ToolCallback> tools = new ArrayList<>(List.of(pick(projectTools, "session_stats", "project_info")));
            tools.addAll(List.of(remoteTools));
            return tools.toArray(ToolCallback[]::new);
        }

        ToolCallback[] allTools() {
            List<ToolCallback> tools = new ArrayList<>(List.of(projectTools));
            tools.addAll(List.of(remoteTools));
            return tools.toArray(ToolCallback[]::new);
        }
    }

    private static ToolCallback[] pick(ToolCallback[] callbacks, String... names) {
        List<String> allowed = List.of(names);
        return java.util.Arrays.stream(callbacks)
                .filter(callback -> allowed.contains(callback.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);
    }

    private ReactAgent node(String name, String instruction, RunContext context) {
        return node(name, instruction, context, null, null, null);
    }

    private ReactAgent node(String name, String instruction, RunContext context, ToolCallback[] tools) {
        return node(name, instruction, context, tools, null, null);
    }

    private ReactAgent node(String name, String instruction, RunContext context,
                            ToolCallback[] tools, String outputKey) {
        return node(name, instruction, context, tools, outputKey, null);
    }

    /**
     * 构造一个编排节点。
     *
     * @param branchDescription 只在路由模式里用到：它会出现在框架拼给模型的「可选分支清单」里，
     *                          路由准不准，一半靠这份描述
     */
    private ReactAgent node(String name, String instruction, RunContext context,
                            ToolCallback[] tools, String outputKey, String branchDescription) {
        Builder builder = ReactAgent.builder()
                .name(name)
                .model(chatModel)
                .instruction(instruction)
                .toolContext(context.toolContext);
        if (branchDescription != null) {
            builder.description(branchDescription);
        }
        if (tools != null && tools.length > 0) {
            builder.tools(tools);
        }
        if (outputKey != null) {
            builder.outputKey(outputKey);
        }
        return builder.build();
    }

    private WorkflowAnswer run(String mode, Long projectId, String sessionId, String question,
                               java.util.function.Function<RunContext, FlowPlan> builder) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        ConversationId conversationId = ConversationId.of(null, projectId, sessionId);
        ledger.append(projectId, conversationId, null,
                com.example.digitalhuman.domain.ChatMessage.Role.USER, question,
                com.example.digitalhuman.domain.ChatMessage.Status.COMPLETED);

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        WorkflowTrace trace = new WorkflowTrace(traceId, mode);
        RunContext context = new RunContext(projectId, sessionId, traceId);
        FlowPlan plan = builder.apply(context);

        try {
            RunOutcome outcome = plan.step().run(context, trace);
            String branch = branchOf(trace, mode);
            return finish(projectId, conversationId, mode, branch, traceId, trace, outcome);
        } catch (GraphRunnerException | RuntimeException primaryFailure) {
            Exception failure = primaryFailure;
            // 编排失败也要有个出口：范围之外的问题不能被硬塞进某个分支，路由失败更不能把请求丢掉
            if (plan.fallback() != null && !"fallback".equals(branchOf(trace, mode))) {
                log.warn("[workflow] trace={} mode={} 编排失败，转入兜底节点：{}",
                        traceId, mode, failure.getMessage());
                try {
                    RunOutcome outcome = invokeWithTracing(plan.fallback(), question, trace);
                    return finish(projectId, conversationId, mode, "fallback", traceId, trace, outcome);
                } catch (GraphRunnerException | RuntimeException fallbackFailure) {                    failure = fallbackFailure;
                }
            }
            ledger.append(projectId, conversationId, null,
                    com.example.digitalhuman.domain.ChatMessage.Role.ASSISTANT,
                    "编排运行失败：" + failure.getMessage(),
                    com.example.digitalhuman.domain.ChatMessage.Status.FAILED);
            if (failure instanceof ModelInvocationException modelException) {
                throw modelException;
            }
            throw new ModelInvocationException(ModelInvocationException.Kind.PROVIDER_ERROR,
                    "workflow", mode, "编排运行失败：" + failure.getMessage(), failure);
        }
    }

    private WorkflowAnswer finish(Long projectId, ConversationId conversationId, String mode,
                                  String branch, String traceId, WorkflowTrace trace,
                                  RunOutcome outcome) {
        ledger.append(projectId, conversationId, null,
                com.example.digitalhuman.domain.ChatMessage.Role.ASSISTANT, outcome.reply(),
                com.example.digitalhuman.domain.ChatMessage.Status.COMPLETED);
        log.info("[workflow] trace={} mode={} branch={} nodes={} outputs={} answerLength={}",
                traceId, mode, branch, trace.nodes(), outcome.outputs().keySet(),
                outcome.reply().length());
        return new WorkflowAnswer(outcome.reply(), mode, branch, traceId, trace.nodes(),
                trace.sequence(), outcome.outputs());
    }

    /**
     * 一次编排计划：**怎么跑**（step）+ 失败时的出口（fallback）。
     *
     * <p>step 做成函数而不是「一个 Agent」，是因为编排层本来就允许「多步 + 中间夹确定性代码」：
     * 并行模式就是「并行召回 → 代码归并 → 回答」三步，只用一个 Agent 表达不了。
     */
    private record FlowPlan(PlanStep step, Agent fallback) {
    }

    @FunctionalInterface
    private interface PlanStep {
        RunOutcome run(RunContext context, WorkflowTrace trace) throws GraphRunnerException;
    }

    /**
     * 跑一次编排，并按节点记录耗时。
     *
     * <p>用 {@code stream} 而不是 {@code invoke}：只有流式输出才带「每个节点的产出」，
     * 这是节点级埋点唯一的来源——这一步决定了「没有节点就没有埋点」这句话能不能落地。
     *
     * <p><b>节点名从哪来</b>（实测踩出来的）：框架吐出的 {@code NodeOutput.node()} 是内部节点 id
     * （{@code _AGENT_MODEL_}、{@code _AGENT_HOOK_InstructionAgentHook.before}、{@code __START__}），
     * 真正对应「哪个节点在跑」的是 {@code NodeOutput.agent()}，形如 {@code subgraph_understand}。
     * 所以埋点用 agent 名（去掉 {@code subgraph_} 前缀），并把同名的连续输出合并成一行——
     * 否则一次简单调用会打印出十几行内部节点，等于没埋点。
     */
    private RunOutcome invokeWithTracing(Agent agent, String question, WorkflowTrace trace)
            throws GraphRunnerException {
        long startedAt = System.nanoTime();
        List<NodeOutput> outputs = new java.util.concurrent.CopyOnWriteArrayList<>();

        // 必须**边到边记**：先 collectList().block() 再遍历的话，所有输出都是流结束后才处理的，
        // 每个节点的耗时都会被记成 0（这条是真实验收时发现的——四个节点全是 0ms，
        // 一眼看上去像「埋点没生效」，其实是采集时机错了）
        agent.stream(question).doOnNext(output -> {
            long atMs = (System.nanoTime() - startedAt) / 1_000_000;
            String name = displayName(output);
            if (name != null) {
                trace.observe(name, atMs);
            }
            outputs.add(output);
        }).blockLast();

        if (outputs.isEmpty()) {
            throw new IllegalStateException("编排没有产出任何节点输出");
        }

        OverAllState state = outputs.get(outputs.size() - 1).state();
        return new RunOutcome(lastAssistantText(state), collectOutputs(state));
    }

    /** 内部节点与流程起止节点不埋点；其余统一用「agent 名去 subgraph_ 前缀」作为节点名。 */
    private static String displayName(NodeOutput output) {
        String agent = output.agent();
        if (agent == null || agent.isBlank()) {
            return null;
        }
        if (agent.startsWith("subgraph_")) {
            return agent.substring("subgraph_".length());
        }
        return agent;
    }

    /** 把各节点写下的状态键读出来（截断展示）：并行分支有没有互相覆盖，看这里最直接。 */
    private static Map<String, String> collectOutputs(OverAllState state) {
        Map<String, String> outputs = new java.util.LinkedHashMap<>();
        for (String key : TRACKED_KEYS) {
            Optional<Object> value = state.value(key);
            if (value.isPresent() && value.get() != null) {
                String text = render(value.get());
                outputs.put(key, text.length() <= 400 ? text : text.substring(0, 400) + "…");
            }
        }
        return outputs;
    }

    /** 状态里的值可能是 Message、List&lt;Message&gt; 或字符串，统一渲染成给人看的文本。 */
    private static String render(Object value) {
        if (value instanceof Message message) {
            return message.getText() == null ? "" : message.getText();
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                parts.add(render(item));
            }
            return String.join("\n---\n", parts);
        }
        return String.valueOf(value);
    }

    private record RunOutcome(String reply, Map<String, String> outputs) {
    }

    /** 从最终状态里取最后一条助手文本：这是「对外仍然是一个字符串」的落点。 */
    private static String lastAssistantText(OverAllState state) {
        Optional<List<Message>> messages = state.value("messages");
        if (messages.isPresent()) {
            List<Message> list = messages.get();
            for (int i = list.size() - 1; i >= 0; i--) {
                Message message = list.get(i);
                if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT
                        && message.getText() != null && !message.getText().isBlank()) {
                    return message.getText();
                }
            }
        }
        // 兜底：有的流程把结果写在自定义 key 上，按常见 key 依次找一遍
        for (String key : List.of("merged_material", "presale_answer", "aftersale_answer",
                "fallback_answer", "clarify", "messages")) {
            Optional<Object> value = state.value(key);
            if (value.isPresent() && value.get() != null) {
                String text = String.valueOf(value.get());
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "抱歉，这次编排没有产出可用的回答。";
    }

    /** 分支名从节点时间线里读出来：路由模式真正走了哪条分支，是状态说了算，不是我们猜。 */
    private static String branchOf(WorkflowTrace trace, String mode) {
        if (!"routed".equals(mode)) {
            return mode;
        }
        for (WorkflowTrace.NodeTiming timing : trace.nodes()) {
            String node = timing.node();
            if ("presale".equals(node) || "aftersale".equals(node) || "fallback".equals(node)) {
                return node;
            }
        }
        return "unknown";
    }
}
