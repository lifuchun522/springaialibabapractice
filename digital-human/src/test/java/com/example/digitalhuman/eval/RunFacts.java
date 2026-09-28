package com.example.digitalhuman.eval;

import java.util.List;

/**
 * 一次运行的「事实」：规则判定只看这些，不看模型自称。
 *
 * <p>{@code toolNames} 来自 {@code tool_call_audit} 审计表，{@code sourceCount} 与 {@code refused}
 * 来自知识问答服务自己返回的结构——**都是我们这边的记录**，不是从回答文本里猜出来的。
 *
 * @param answer      回答原文
 * @param toolNames   真实发生过的工具调用（不是模型说它调了）
 * @param sourceCount  召回到的来源条数；不适用时为 null
 * @param refused      是否走了系统拒答分支；不适用时为 null
 */
public record RunFacts(String answer, List<String> toolNames, Integer sourceCount, Boolean refused) {

    public RunFacts {
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
    }

    public static RunFacts ofAnswer(String answer) {
        return new RunFacts(answer, List.of(), null, null);
    }
}
