package com.example.digitalhuman.multiagent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三个角色的边界：**提示词、工具、记忆，三者各自封闭**。
 *
 * <p>这一掌的硬约束在这里落地：<b>任何两个角色不共享同一个工具</b>。
 * 为什么要把这条写死：共用工具清单意味着「每个角色单次决策的候选空间」没有变窄，
 * 拆多 Agent 的第一收益（上下文更窄）就没了，只剩多次模型调用的成本。
 *
 * <p>记忆边界同理：三个角色各一份记忆，会话 ID 相同但**命名空间不同**，
 * 避免上一轮的检索片段污染这一轮的查询参数。
 */
public final class MultiAgentRoles {

    public static final String RECEPTION = "reception";
    public static final String KNOWLEDGE = "knowledge";
    public static final String BUSINESS = "business";
    public static final String HUMAN = "human";

    /** 工具归属：名字 → 只属于哪个角色。这是「工具边界」的唯一真源，测试也读它。 */
    private static final Map<String, List<String>> TOOLS_BY_ROLE = Map.of(
            // 接待只掌握项目元信息（标题/主题色/开场白），不碰知识库与业务数据
            RECEPTION, List.of("project_info"),
            // 知识只做检索，不做业务查询
            KNOWLEDGE, List.of("knowledge_search"),
            // 业务只查实时数据（会话统计 + 远程展厅预约），不复述文档
            BUSINESS, List.of("session_stats", "showroom_query_availability"));

    /** 允许的路由目标（Router 的分类结果必须落在这个集合里，否则收敛到接待）。 */
    public static final List<String> ROUTABLE = List.of(RECEPTION, KNOWLEDGE, BUSINESS);

    private MultiAgentRoles() {
    }

    public static List<String> toolsOf(String role) {
        return TOOLS_BY_ROLE.getOrDefault(role, List.of());
    }

    public static Map<String, List<String>> allTools() {
        return new LinkedHashMap<>(TOOLS_BY_ROLE);
    }

    /** 记忆命名空间：会话 ID 相同，但角色各自一份，互不可见。 */
    public static String memoryNamespace(String role, String sessionId) {
        return role + ":" + (sessionId == null || sessionId.isBlank() ? "default" : sessionId);
    }

    public static boolean isRoutable(String role) {
        return ROUTABLE.contains(role);
    }
}
