package com.example.digitalhuman.eval;

/**
 * 一条回归用例。
 *
 * <p>字段的设计意图，就是第 15 掌那条红线在数据上的投影：
 * <ul>
 *   <li>{@code mode=LIVE} 表示需要真实模型才能跑（回答措辞不可控，只能规则 + Judge）；
 *       {@code mode=OFFLINE} 表示这一类行为已被某条确定性用例钉住，
 *       由 {@code offlineReference} 指到具体类与方法——评测集不做重复劳动，只做索引；</li>
 *   <li>{@code determinism} 是作者在写用例前必须先回答的那个问题：
 *       这条断言属于「确定性 / 规则可判 / 需语义判断」哪一类。</li>
 * </ul>
 *
 * @param id                用例编号，全局唯一（回归集是资产，资产要有编号才能被引用）
 * @param mode              LIVE 或 OFFLINE
 * @param input             送给系统的输入
 * @param determinism       DETERMINISTIC / RULE / SEMANTIC
 * @param expect            规则期望；OFFLINE 用例可只填 offlineReference
 * @param judgeCriterion    交给 Judge 的判据原文；为空表示这条用例不做语义评分
 * @param offlineReference  该行为已被哪条离线用例钉住（类#方法）
 */
public record RegressionCase(String id,
                             String mode,
                             String input,
                             String determinism,
                             Expect expect,
                             String judgeCriterion,
                             String offlineReference) {

    public static final String MODE_LIVE = "LIVE";
    public static final String MODE_OFFLINE = "OFFLINE";

    public boolean live() {
        return MODE_LIVE.equals(mode);
    }

    public boolean needsJudge() {
        return judgeCriterion != null && !judgeCriterion.isBlank();
    }

    public Expect expectOrEmpty() {
        return expect == null ? new Expect(null, null, null, null, null, null, null, null) : expect;
    }
}
