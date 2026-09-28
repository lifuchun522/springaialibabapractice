package com.example.digitalhuman.a2a;

import java.util.List;

/**
 * 发现层：**只回答「谁能被找到」**，不承载业务语义。
 *
 * <p>为什么把它抽成接口（这一掌的工程要点）：注册中心是可选中间层。
 * 本地单实例开发时一个静态表就够了；多实例、要扩缩容时才需要 Nacos。
 * 把发现抽成接口之后，主服务的 A2A 调用逻辑完全不需要知道背后是哪种实现——
 * 换来的是「Nacos 挂了不影响单实例跑」和「本地验证不需要起注册中心」。
 */
public interface AgentRegistry {

    /** 当前可用的知识 Agent 实例（按注册顺序）。 */
    List<AgentInstance> instances();

    /** 选下一个实例：轮询，多实例时能观察到流量被分散（验收第二条）。 */
    AgentInstance next();

    /**
     * @param instanceId 实例标识（日志对账用，Nacos 走 metadata）
     */
    record AgentInstance(String instanceId, String baseUrl) {
    }
}
