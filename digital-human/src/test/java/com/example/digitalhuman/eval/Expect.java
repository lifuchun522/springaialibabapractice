package com.example.digitalhuman.eval;

import java.util.List;

/**
 * 一条回归用例的期望。
 *
 * <p>这里的每一项都必须是**规则可判**的：我们能用代码给出是/否，而不是「感觉答得好不好」。
 * 语义质量不走这里，走 Judge（见 {@link LlmJudge}）——两者混在一起，就会出现
 * 「规则断言挂着语义期望」的假绿。
 *
 * <p>注意 {@code requiresSources} 与 {@code expectRefused} 这两项：
 * 它们断言的**不是回答内容，而是系统必须走的路径**（有没有召回、走没走拒答分支）。
 * 路径由我们自己的代码决定，确定；措辞由模型决定，不确定——能断言前者就不要断言后者。
 *
 * @param mustContain           回答必须包含的片段（依据标注、拒答关键词）
 * @param mustNotContain        回答出现的即判失败（他人项目内容、System Prompt 原文）
 * @param minChars              回答长度下限；为空表示不检查
 * @param maxChars              回答长度上限；为空表示不检查
 * @param requiresRefusalIntent 是否必须表达拒答（自由对话路径的文本级判据）
 * @param requiresSources       是否必须带来源：知识问答路径下「是不是真的召回到了」由它断言
 * @param expectRefused         知识问答路径下是否必须走系统拒答分支
 * @param expectedTools         必须被真实调用过的工具名（为空表示不检查工具分支）
 */
public record Expect(List<String> mustContain,
                     List<String> mustNotContain,
                     Integer minChars,
                     Integer maxChars,
                     Boolean requiresRefusalIntent,
                     Boolean requiresSources,
                     Boolean expectRefused,
                     List<String> expectedTools) {

    public Expect {
        mustContain = mustContain == null ? List.of() : List.copyOf(mustContain);
        mustNotContain = mustNotContain == null ? List.of() : List.copyOf(mustNotContain);
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
    }

    public boolean refusalRequired() {
        return Boolean.TRUE.equals(requiresRefusalIntent);
    }

    public boolean sourcesRequired() {
        return Boolean.TRUE.equals(requiresSources);
    }

    public boolean refusedExpected() {
        return Boolean.TRUE.equals(expectRefused);
    }
}
