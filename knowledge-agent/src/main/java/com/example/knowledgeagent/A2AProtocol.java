package com.example.knowledgeagent;

import java.util.List;
import java.util.Map;

/**
 * A2A 协议里对外的三种东西：**能力声明（AgentCard）、任务（Task）、事件（TaskEvent）**。
 *
 * <p>为什么契约是这两样而不是方法签名（这一掌的核心判断）：
 * 跨服务协作时，对方首先要知道「你是谁、你会什么、我怎么访问你」（能力声明），
 * 其次要知道「我交给你的那件事现在到哪一步了」（任务状态）。
 * 有了这两样才谈得出版本协商、超时语义与可观测；只有方法签名的话，
 * 一次调用失败到底是「没到」还是「在路上」还是「做完了没回」根本说不清。
 */
public final class A2AProtocol {

    /** 协议版本：主服务与知识 Agent 必须协商一致，否则显式失败（验收第四条）。 */
    public static final String VERSION = "1.0";

    /** 请求头里带协议版本；服务端不匹配时返回 409，而不是「尽力解析后给出错答案」。 */
    public static final String VERSION_HEADER = "A2A-Version";

    /** 贯穿标识：主服务的日志与知识 Agent 的日志用它串起来（验收第三条）。 */
    public static final String TRACE_HEADER = "A2A-Trace-Id";

    private A2AProtocol() {
    }

    /** 能力声明：我是谁、我会什么、我怎么被访问。 */
    public record AgentCard(String name, String description, String version, String url,
                            List<Skill> skills, Capabilities capabilities) {

        public record Skill(String id, String name, String description) {
        }

        /** 协议层能力：是否支持流式（部分结果是一种合法的中间状态，不是异常）。 */
        public record Capabilities(boolean streaming, boolean taskLifecycle) {
        }
    }

    /** 任务状态机：submitted → working → completed / failed / canceled。 */
    public enum TaskState {
        SUBMITTED, WORKING, COMPLETED, FAILED, CANCELED
    }

    public record Task(String taskId, String traceId, TaskState state, String question,
                       String answer, List<Map<String, String>> artifacts, String error,
                       long createdAt, long updatedAt) {
    }

    /** 流式事件：客户端边到边消费，首字延迟不必等整个任务跑完。 */
    public record TaskEvent(String taskId, TaskState state, String delta) {
    }
}
