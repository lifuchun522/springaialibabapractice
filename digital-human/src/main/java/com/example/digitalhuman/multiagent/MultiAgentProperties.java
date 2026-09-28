package com.example.digitalhuman.multiagent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多 Agent 编排的边界与角色提示词。
 *
 * @param maxHops        一跳最多换几个角色：**上界必须由代码握着**，不能指望模型自己停
 * @param skills         能力说明（按需注入，不进主提示词）——这是「上下文更窄」的关键手法之一
 * @param roleInstructions 每个角色的提示词；缺省值在下面给出
 */
@ConfigurationProperties(prefix = "digital-human.multi-agent")
public record MultiAgentProperties(int maxHops,
                                   Map<String, String> skills,
                                   Map<String, String> roleInstructions) {

    public MultiAgentProperties {
        if (maxHops <= 0) {
            maxHops = 3;
        }
        if (skills == null || skills.isEmpty()) {
            skills = new LinkedHashMap<>(Map.of(
                    "project-meta", "项目元信息口径：字段有标题、主题色、开场白、结束语；改动前必须确认用户明确要求。",
                    "metrics-glossary", "业务指标口径：会话条数 = 该会话落在账本里的消息条数；用户提问次数只数用户侧。",
                    "escalation", "转人工口径：用户明确要求人工，或连续两次表达不满时，回复末尾输出 handoff=human。"));
        }
        if (roleInstructions == null || roleInstructions.isEmpty()) {
            roleInstructions = new LinkedHashMap<>(Map.of(
                    MultiAgentRoles.RECEPTION, """
                            你是数字人「小盟」的主接待。只做寒暄、身份确认、需求澄清，
                            你只掌握项目元信息（标题、主题色、开场白、结束语）。
                            如果用户问的是项目资料或实时数据，不要自己回答：
                            在回复的最后单独一行输出 handoff=knowledge 或 handoff=business。
                            需要人工时输出 handoff=human。
                            """,
                    MultiAgentRoles.KNOWLEDGE, """
                            你是项目知识 Agent，只回答项目知识库里已索引的内容，回答要标出来源 [n]。
                            检索不到就明确说「资料里没有相关内容」，禁止猜测。
                            不输出寒暄，不查业务实时数据。
                            """,
                    MultiAgentRoles.BUSINESS, """
                            你是业务查询 Agent，只负责把自然语言转成参数并调用业务工具
                            （会话统计、展厅预约余位），只输出查询结果与口径。
                            查不到就说查不到，不要猜，也不复述文档原理。
                            """));
        }
    }

    public String instructionOf(String role) {
        return roleInstructions.getOrDefault(role, "");
    }

    /**
     * 按需注入能力说明：**只给这个角色用得上的那几条**。
     *
     * <p>为什么不把所有能力说明都写进每个角色的提示词：那正是这一掌要解决的问题
     * （提示词随职责线性变长、每次调用都付这份固定成本）。
     */
    public String skillsFor(String role) {
        List<String> names = switch (role) {
            case MultiAgentRoles.RECEPTION -> List.of("project-meta", "escalation");
            case MultiAgentRoles.BUSINESS -> List.of("metrics-glossary");
            default -> List.of();
        };
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("\n可用能力说明：\n");
        for (String name : names) {
            text.append('-').append(name).append("：").append(skills.get(name)).append('\n');
        }
        return text.toString();
    }
}
