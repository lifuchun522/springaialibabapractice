package com.example.digitalhuman.a2a;

import java.util.List;

/**
 * A2A 协议里的三种东西（客户端侧的副本）。
 *
 * <p>这份定义是**协议契约**，与知识 Agent 服务端的实现无关：
 * 主服务不依赖对方的任何类（验收第一条），它只依赖「能力声明 + 任务状态」这两个形状。
 * 两边各写一份是必要的代价——共享一份的话，S是用编译期依赖换掉了协议边界，
 * 而那正是拆服务要摆脱的东西。
 */
public final class A2AProtocol {

    public static final String VERSION = "1.0";
    public static final String VERSION_HEADER = "A2A-Version";
    public static final String TRACE_HEADER = "A2A-Trace-Id";

    private A2AProtocol() {
    }

    public record AgentCard(String name, String description, String version, String url,
                            List<Skill> skills, Capabilities capabilities) {

        public record Skill(String id, String name, String description) {
        }

        public record Capabilities(boolean streaming, boolean taskLifecycle) {
        }
    }

    public enum TaskState {
        SUBMITTED, WORKING, COMPLETED, FAILED, CANCELED
    }
}
