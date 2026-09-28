package com.example.digitalhuman.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;

/**
 * 售后图的状态键与**归约策略**。
 *
 * <p>这一掌最反直觉的一句话：**节点不修改状态，节点只是提交变更请求，真正决定状态的是归约规则。**
 * 节点代码可以完全正确，策略配错，结果照样是错的——而且错得很安静：
 * 不报异常、不打日志，只是数据没了。文章里那次「并行跑完之后知识片段整段消失」就是这个坑。
 *
 * <p>所以这里的写法是：**用到的每个 key 都显式声明策略，一个都不留给默认行为**。
 * 默认行为等价于 REPLACE（后者覆盖前者），在并行分支上就是静默丢数据。
 *
 * <p>键的类型分三类，策略也就分三类：
 * <ul>
 *   <li><b>单值事实</b>（意图、风险等级、人工决定、最终答复）：REPLACE —— 后写的确实该覆盖前写的；</li>
 *   <li><b>累积证据</b>（知识片段、业务记录）：APPEND —— 并行分支各自提交，谁都不该被挤掉；</li>
 *   <li><b>消息列表</b>：APPEND —— 消息是追加语义，覆盖等于把对话历史删了。</li>
 * </ul>
 */
public final class AfterSaleState {

    /** 输入：用户这一句话。 */
    public static final String INPUT = "input";
    /** 意图分类结果。 */
    public static final String INTENT = "intent";
    /** 知识库命中的片段（并行分支之一，累积）。 */
    public static final String KNOWLEDGE_HITS = "knowledgeHits";
    /** 业务系统取回的记录（并行分支之一，累积）。 */
    public static final String BIZ_RECORDS = "bizRecords";
    /** 风险等级：HIGH / LOW。 */
    public static final String RISK_LEVEL = "riskLevel";
    /** 人工决定：APPROVE / REJECT / 空（未决）。 */
    public static final String HUMAN_DECISION = "humanDecision";
    /** 最终答复。 */
    public static final String REPLY = "reply";
    /** 消息列表（Agent 节点会往里写）。 */
    public static final String MESSAGES = "messages";
    /**
     * 节点自报日志（累积）。
     *
     * <p>为什么除了框架的事件流还要自己记一份：实测发现**并行分支里的普通节点不会单独产生
     * NodeOutput**（它和 Agent 节点在同一个 super-step 里，事件流里只看得见 Agent 那一格）。
     * 于是「该查的没查」这种问题恰恰在事件流里看不出来——而这正是本掌要解决的事。
     * 自报日志写在状态里，除了补全埋点，还跟着检查点一起落盘：**重启之后仍然知道之前跑过哪些节点**。
     */
    public static final String NODE_LOG = "nodeLog";

    /** 节点名：中断点写在 HUMAN_REVIEW 上，恢复时从这里继续。 */
    public static final String NODE_INTENT = "intent";
    public static final String NODE_KNOWLEDGE = "knowledge";
    public static final String NODE_BIZ = "biz";
    public static final String NODE_RISK = "risk";
    public static final String NODE_HUMAN_REVIEW = "humanReview";
    public static final String NODE_REPLY = "reply";

    /** 会话标识：运行时配置，不是业务状态（混进状态里恢复就会失败，见 docs/ch11-验收记录.md）。 */
    public static final String THREAD_ID_ARGUMENT = "threadId";

    private AfterSaleState() {
    }

    /**
     * 策略表：**每个 key 都显式声明**。
     *
     * <p>注意 {@link #KNOWLEDGE_HITS} 与 {@link #BIZ_RECORDS}：并行分支同时提交，
     * 声明成 APPEND 才不会被后者覆盖。这一条是本掌验收里专门要证明的。
     */
    public static KeyStrategyFactory strategies() {
        return () -> {
            Map<String, KeyStrategy> map = new LinkedHashMap<>();
            map.put(INPUT, KeyStrategy.REPLACE);
            map.put(INTENT, KeyStrategy.REPLACE);
            map.put(KNOWLEDGE_HITS, KeyStrategy.APPEND);
            map.put(BIZ_RECORDS, KeyStrategy.APPEND);
            map.put(RISK_LEVEL, KeyStrategy.REPLACE);
            map.put(HUMAN_DECISION, KeyStrategy.REPLACE);
            map.put(REPLY, KeyStrategy.REPLACE);
            map.put(MESSAGES, KeyStrategy.APPEND);
            map.put(NODE_LOG, KeyStrategy.APPEND);
            return map;
        };
    }

    /**
     * 故意配错的策略表：把累积型的 key 留给默认行为（等价 REPLACE）。
     *
     * <p>为什么代码里要留一份「错的配置」：**这个坑不报错，只丢数据**。
     * 把它留成测试里可运行的对照，比在文档里写一句「不要这样写」有用得多——
     * 见 {@code AfterSaleGraphTest#appendStrategy_shouldBeatReplaceOnParallelBranches}。
     */
    public static KeyStrategyFactory replaceEverythingStrategies() {
        return () -> {
            Map<String, KeyStrategy> map = new LinkedHashMap<>();
            map.put(INPUT, KeyStrategy.REPLACE);
            map.put(INTENT, KeyStrategy.REPLACE);
            map.put(KNOWLEDGE_HITS, KeyStrategy.REPLACE);   // ← 坑就在这里
            map.put(BIZ_RECORDS, KeyStrategy.REPLACE);
            map.put(RISK_LEVEL, KeyStrategy.REPLACE);
            map.put(HUMAN_DECISION, KeyStrategy.REPLACE);
            map.put(REPLY, KeyStrategy.REPLACE);
            map.put(MESSAGES, KeyStrategy.APPEND);
            map.put(NODE_LOG, KeyStrategy.APPEND);
            return map;
        };
    }

    /** 把状态里的累积型值读成字符串列表（APPEND 结果是 List，单值可能是 String）。 */
    public static List<String> asTextList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }
}
