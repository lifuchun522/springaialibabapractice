package com.example.digitalhuman.tools;

/**
 * ToolContext 的键。
 *
 * <p>这些都是**旁路身份**：不进工具参数的 JSON Schema，模型看不见、也填不了。
 * 「身份永远不能由模型填」是这一掌最硬的一条边界——只要租户号/用户号出现在参数列表里，
 * 模型就有机会把它填成别人的。
 */
public final class ToolContextKeys {

    public static final String PROJECT_ID = "projectId";
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String SESSION_ID = "sessionId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String TRACE_ID = "traceId";

    private ToolContextKeys() {
    }
}
