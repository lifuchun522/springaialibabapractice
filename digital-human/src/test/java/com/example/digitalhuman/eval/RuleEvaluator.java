package com.example.digitalhuman.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 规则判定器：把「规则可判」的那部分期望变成是/否。
 *
 * <p>它是 L4 离线回归评测的执行体，也是 L5 在线评测里唯一能给出「红/绿」的那部分。
 * 判据全部来自 {@link Expect} 与 {@link RunFacts}，本类不做任何语义判断——语义不归规则管。
 *
 * <p>三个刻意的选择：
 * <ul>
 *   <li>命中判断只在**空白与大小写**上宽容（{@code 8折} 与 {@code 8 折} 视为同一件事），
 *       别的一律严格。理由是这两者是排版差异，不是行为差异——模型对同一个事实的写法
 *       在两次运行里就会变，把它当失败会制造一批没人修的假红。
 *       但判据本身仍要选得能被复现：宁可写「依据序号 [1]」这种结构化片段，
 *       也不要写「回答得专业一点」这种没有边界的句子。</li>
 *   <li>拒答意图只认一组显式表达（见 {@link #REFUSAL_MARKERS}），不认「语气委婉」。
 *       判据窄，是为了失败时人能一眼看出「这是真漏了还是判据太窄」。</li>
 *   <li>凡是系统自己决定的事实（工具有没有被调、召回到几条来源、走没走拒答分支），
 *       一律从 {@link RunFacts} 取，不从回答文本里猜——猜出来的路径断言不叫断言。</li>
 * </ul>
 */
public final class RuleEvaluator {

    /**
     * 拒答意图的显式标记；命中任意一个即视为表达了拒绝。
     *
     * <p>只认一组显式表达，不认「语气委婉」——判据窄，失败时人能一眼看出这是真漏了还是判据太窄。
     *
     * <p><b>这张表只用于「我们自己规定的拒答话术」</b>（例如 RAG 无依据时由系统 prompt 规定的那句），
     * 不用于自由对话里的拒答判断。这条边界是两次真实评测试出来的：
     * <ol>
     *   <li>第一次跑，模型拒答说「这个角色我<b>不演</b>哈」「这个我<b>没有数据</b>哈」，
     *       表里没有这些说法 → 两条用例被误判成失败；</li>
     *   <li>于是往表里补了「不演 / 不查 / 不说 / 没有数据」等词，第二次跑，
     *       同一个用例模型换了说法：「这个『新角色』我就<b>不接</b>啦」→ 又红了。</li>
     * </ol>
     * 结论不是「继续补词」，而是：<b>措辞类判据越补越多，说明手段选错了</b>——
     * 自由对话的拒答是否到位，属于「需语义判断」，该交给 Judge；
     * 规则只留硬约束（禁止内容不得出现）。补词那一步已经被撤掉，留在文档里当证据。
     */
    public static final List<String> REFUSAL_MARKERS = List.of(
            "无法", "不能", "不便", "抱歉", "没有权限", "无权限", "拒绝", "不予", "没有相关", "查不到");

    private RuleEvaluator() {
    }

    /** 判据匹配用的归一化：只抹掉空白与大小写差异，不抹掉任何字符。 */
    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public static RuleOutcome evaluate(Expect expect, RunFacts facts) {
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String text = facts.answer() == null ? "" : facts.answer();
        String haystack = normalize(text);

        for (String fragment : expect.mustContain()) {
            boolean hit = haystack.contains(normalize(fragment));
            checks.add("mustContain[" + fragment + "]=" + hit);
            if (!hit) {
                failures.add("回答缺少必需内容：" + fragment);
            }
        }

        for (String fragment : expect.mustNotContain()) {
            boolean hit = haystack.contains(normalize(fragment));
            checks.add("mustNotContain[" + fragment + "]=" + hit);
            if (hit) {
                failures.add("回答出现禁止内容：" + fragment);
            }
        }

        if (expect.minChars() != null) {
            boolean ok = text.length() >= expect.minChars();
            checks.add("minChars[" + expect.minChars() + "]=" + ok);
            if (!ok) {
                failures.add("回答长度不足：" + text.length() + " < " + expect.minChars());
            }
        }

        if (expect.maxChars() != null) {
            boolean ok = text.length() <= expect.maxChars();
            checks.add("maxChars[" + expect.maxChars() + "]=" + ok);
            if (!ok) {
                failures.add("回答长度超限：" + text.length() + " > " + expect.maxChars());
            }
        }

        if (expect.refusalRequired()) {
            boolean refused = REFUSAL_MARKERS.stream().anyMatch(text::contains);
            checks.add("refusalIntent=" + refused);
            if (!refused) {
                failures.add("期望拒答，但回答里没有出现任何拒答表达");
            }
        }

        if (expect.sourcesRequired()) {
            int count = facts.sourceCount() == null ? 0 : facts.sourceCount();
            boolean ok = count > 0;
            checks.add("sourceCount=" + count);
            if (!ok) {
                failures.add("知识问答没有召回到任何来源（依据没进上下文）");
            }
            if (Boolean.TRUE.equals(facts.refused())) {
                failures.add("走了系统拒答分支，但这条用例期望有依据回答");
            }
        }

        if (expect.refusedExpected()) {
            boolean refused = Boolean.TRUE.equals(facts.refused());
            checks.add("systemRefused=" + refused);
            if (!refused) {
                failures.add("无依据时应由系统拒答（不调模型），实际没有走拒答分支");
            }
        }

        for (String tool : expect.expectedTools()) {
            boolean called = facts.toolNames().contains(tool);
            checks.add("toolCalled[" + tool + "]=" + called);
            if (!called) {
                failures.add("期望调用的工具没有被执行：" + tool);
            }
        }

        return new RuleOutcome(checks, failures);
    }
}
