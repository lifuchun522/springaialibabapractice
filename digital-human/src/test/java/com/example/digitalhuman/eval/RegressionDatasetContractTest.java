package com.example.digitalhuman.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1：回归集本身也要被断言。
 *
 * <p>第 15 掌说「回归集是资产」——资产的意思是它像代码一样被管理：有版本、有归属、有评审。
 * 这份用例就是它的门禁，拦三类退化的方式：
 * <ul>
 *   <li>少一类：六类必须齐全，少一类就红（而不是「反正没人发现」）；</li>
 *   <li>作者没回答「这条断言属于哪一类」：{@code determinism} 必须显式写明；</li>
 *   <li>引用烂掉：OFFLINE 用例指向的确定性用例必须真实存在——
 *       否则一条用例被删掉之后，评测集里还挂着一个指向空气的引用，
 *       报告上却显示「已由离线用例覆盖」。这是最安静的一种假绿。</li>
 * </ul>
 *
 * <p>它跑在 CI 的阻断路径里：数据集坏了属于「确定性」问题，不该等评测任务才发现。
 */
class RegressionDatasetContractTest {

    /** knowledge-agent 是另一个模块，它的用例由该模块自己的构建保证存在。 */
    private static final String CROSS_MODULE_PREFIX = "com.example.knowledgeagent.";

    @Test
    @DisplayName("六类回归集必须齐全，且每类都带版本号")
    void shouldHaveAllSixSuitesWithVersion() {
        List<RegressionSuite> suites = RegressionDataset.loadAll();

        assertThat(suites).hasSize(6);
        assertThat(suites).extracting(RegressionSuite::suite)
                .containsExactlyElementsOf(RegressionDataset.SUITE_FILES);
        for (RegressionSuite suite : suites) {
            assertThat(suite.version()).as("数据集版本不能为空：%s", suite.suite()).isNotBlank();
            assertThat(suite.displayName()).as("数据集中文名不能为空：%s", suite.suite()).isNotBlank();
            assertThat(suite.cases()).as("回归集不能是空的：%s", suite.suite()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("用例编号全局唯一，且每条都必须声明确定性类别")
    void shouldHaveUniqueIdsAndDeclaredDeterminism() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (RegressionDataset.CaseRef ref : RegressionDataset.allCases()) {
            RegressionCase item = ref.item();
            assertThat(item.id()).as("用例编号不能为空").isNotBlank();
            if (!seen.add(item.id())) {
                duplicates.add(item.id());
            }
            assertThat(item.input()).as("用例输入不能为空：%s", item.id()).isNotBlank();
            assertThat(item.determinism())
                    .as("用例必须声明确定性类别（DETERMINISTIC / RULE / RULE+JUDGE / SEMANTIC）：%s", item.id())
                    .isIn("DETERMINISTIC", "RULE", "RULE+JUDGE", "SEMANTIC");
            assertThat(item.mode())
                    .as("用例模式只能是 LIVE 或 OFFLINE：%s", item.id())
                    .isIn(RegressionCase.MODE_LIVE, RegressionCase.MODE_OFFLINE);
        }

        assertThat(duplicates).as("用例编号重复").isEmpty();
    }

    @Test
    @DisplayName("LIVE 用例必须有期望；OFFLINE 用例必须有真实存在的离线引用")
    void shouldKeepExpectationsAndOfflineReferencesValid() {
        for (RegressionDataset.CaseRef ref : RegressionDataset.allCases()) {
            RegressionCase item = ref.item();
            if (item.live()) {
                assertThat(item.expect())
                        .as("LIVE 用例必须给出期望，否则它只是在跑一遍模型：%s", item.id())
                        .isNotNull();
            } else {
                assertThat(item.offlineReference())
                        .as("OFFLINE 用例必须写明由哪条确定性用例覆盖：%s", item.id())
                        .isNotBlank();
                assertReferenceResolves(item);
            }
        }
    }

    private static void assertReferenceResolves(RegressionCase item) {
        String reference = item.offlineReference();
        if (reference.startsWith(CROSS_MODULE_PREFIX)) {
            // 跨模块引用由 knowledge-agent 模块自己的测试运行时保证
            return;
        }
        assertThat(canLoad(reference))
                .as("离线引用指向的用例类不存在（引用烂掉 = 报告上的假覆盖）：%s -> %s", item.id(), reference)
                .isTrue();
    }

    private static boolean canLoad(String className) {
        try {
            Class.forName(className, false, RegressionDatasetContractTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
