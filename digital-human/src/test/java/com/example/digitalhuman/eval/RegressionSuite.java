package com.example.digitalhuman.eval;

import java.util.List;

/**
 * 一类回归集（六类之一）。
 *
 * @param suite       数据集标识，与文件名一致
 * @param version     数据集版本：评测记录里必须带上它，否则「这次分数比上次低」无法归因
 * @param displayName 中文名，用于报告
 * @param cases       用例列表
 */
public record RegressionSuite(String suite, String version, String displayName, List<RegressionCase> cases) {

    public RegressionSuite {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
