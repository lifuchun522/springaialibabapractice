package com.example.digitalhuman.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 运行时边界（全部是配置，不是代码里的魔数）。
 *
 * @param modelCallLimit  单次用户请求允许的模型调用次数上限。
 *                        这不是「不信任模型」，而是替模型承担它看不见的约束：
 *                        越强的模型探索意愿越强，越需要硬边界。
 * @param instruction     Agent 的行为风格（instruction 管风格，Hooks 管边界，两者不能互相替代）
 *
 * <p>工具重试次数<b>不在这里</b>：重试属于工具边界（{@code digital-human.tools.max-retries}），
 * 一个策略只能有一个配置入口，两处都能配等于两处都会配错。
 */
@ConfigurationProperties(prefix = "digital-human.agent")
public record DigitalHumanAgentProperties(int modelCallLimit, String instruction) {

    public DigitalHumanAgentProperties {
        if (modelCallLimit <= 0) {
            modelCallLimit = 8;
        }
        if (instruction == null || instruction.isBlank()) {
            instruction = "你是数字人助手。需要事实时优先调用工具或知识库，不要凭记忆编造。"
                    + "回答简短、口语化，不要输出 Markdown 表格。";
        }
    }
}
