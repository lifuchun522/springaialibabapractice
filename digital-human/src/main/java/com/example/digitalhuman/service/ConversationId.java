package com.example.digitalhuman.service;

import java.util.regex.Pattern;

/**
 * 会话键。规则只有一条，但必须定死：
 *
 * <pre>conversationId = userId:projectId:sessionId</pre>
 *
 * <p>为什么是三层：项目隔离不等于会话隔离。同一项目下两个浏览器各自有 sessionId，
 * 只按 projectId 做 key，两个用户的历史会互相污染（文章第 06 节第一条排查链就是这个现象）。
 *
 * <p>为什么不用默认值兜底当 key：Advisor 一旦拿不到 conversationId，Memory 会全部落到默认 key 上，
 * 表现为「日志里 key 都一样」——所以这里把缺失分量显式写成 {@code anon} / {@code 0} / {@code default}，
 * 让它在日志里一眼可辨，而不是悄悄共享一段上下文。
 */
public record ConversationId(String value) {

    private static final String ANONYMOUS = "anon";
    private static final String NO_PROJECT = "0";
    private static final String DEFAULT_SESSION = "default";
    private static final Pattern SESSION_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public ConversationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
    }

    public static ConversationId of(Long userId, Long projectId, String sessionId) {
        String user = userId == null ? ANONYMOUS : String.valueOf(userId);
        String project = projectId == null ? NO_PROJECT : String.valueOf(projectId);
        return new ConversationId(user + ":" + project + ":" + normalizeSession(sessionId));
    }

    /** sessionId 由前端提供，必须是可控字符集，避免拼进 key 后无法排查。 */
    public static String normalizeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return DEFAULT_SESSION;
        }
        String trimmed = sessionId.trim();
        if (!SESSION_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("sessionId 只允许字母、数字、下划线与短横线，长度 1~64：" + sessionId);
        }
        return trimmed;
    }

    /** 账本里单独存一份 sessionId，便于按项目+会话直接查历史。 */
    public String sessionId() {
        return value.substring(value.lastIndexOf(':') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
