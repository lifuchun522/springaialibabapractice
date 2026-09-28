package com.example.digitalhuman.eval;

import java.util.List;

/**
 * 一条用例的规则判定结果。
 *
 * @param checks   每一项检查的原文（进入运行时记录，失败时不用猜是哪一条挂的）
 * @param failures 未通过的检查
 */
public record RuleOutcome(List<String> checks, List<String> failures) {

    public RuleOutcome {
        checks = List.copyOf(checks);
        failures = List.copyOf(failures);
    }

    public boolean passed() {
        return failures.isEmpty();
    }
}
