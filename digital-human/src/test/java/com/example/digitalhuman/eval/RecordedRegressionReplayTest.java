package com.example.digitalhuman.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * L4 离线回归评测：把真实模型的输出快照重放一遍。
 *
 * <p>它与 L1/L2 的区别不是「更高级」，而是**服务不同的决策**：
 * L1/L2 决定「这次改动能不能合并」，L4 回答「判据、期望、数据集这一侧动了之后，
 * 有多少条结论被改变了」。后者不该阻断合并——但它必须被显式看见。
 *
 * <p>所以这里断言的是「重放结论与录下来的基线一致」，不是「全部通过」：
 * 一条真实存在的失败用例，它就该稳定地失败，直到有人去修它或修判据。
 * 把基线断言成「全绿」等于要求评测集永远报喜，那是评测最容易退化成仪式的方式。
 *
 * <p>它被打上 {@code eval} tag：离线、几秒跑完、不调模型，适合放进 nightly。
 */
@Tag("eval")
class RecordedRegressionReplayTest {

    @Test
    @DisplayName("L4：真实输出快照重放，结论必须与基线一致")
    void shouldReplayRecordedAnswersAgainstBaseline() {
        RecordedBaseline baseline = RecordedBaseline.load();
        List<RegressionDataset.CaseRef> liveCases = RegressionDataset.allCases().stream()
                .filter(ref -> ref.item().live())
                .toList();

        Map<String, RecordedBaseline.Entry> recorded = new HashMap<>();
        for (RecordedBaseline.Entry entry : baseline.entries()) {
            recorded.put(entry.id(), entry);
        }

        List<String> replayPass = new ArrayList<>();
        List<String> replayFail = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (RegressionDataset.CaseRef ref : liveCases) {
            RecordedBaseline.Entry entry = recorded.get(ref.item().id());
            if (entry == null) {
                missing.add(ref.item().id());
                continue;
            }
            RuleOutcome outcome = RuleEvaluator.evaluate(ref.item().expectOrEmpty(), entry.toFacts());
            if (outcome.passed()) {
                replayPass.add(ref.item().id());
            } else {
                replayFail.add(ref.item().id() + " → " + outcome.failures());
            }
        }

        System.out.printf("""
                == L4 离线重放 ==
                基线版本 : %s（模型 %s，录制于 %s）
                用例覆盖 : %d / %d 条 LIVE 用例有快照
                重放通过 : %d 条
                重放失败 : %d 条
                %s""",
                baseline.datasetVersion(), baseline.model(), baseline.recordedAt(),
                recorded.size(), liveCases.size(), replayPass.size(), replayFail.size(),
                replayFail.isEmpty() ? "" : "失败明细：" + String.join("；", replayFail) + System.lineSeparator());

        assertThat(missing).as("LIVE 用例没有录到快照，L4 的覆盖面就是假的").isEmpty();
        assertThat(replayPass).as("重放结论与基线不一致：说明判据或数据集被改动过，需要显式确认")
                .containsExactlyInAnyOrderElementsOf(baseline.passIds());
    }

    @Test
    @DisplayName("L4：快照里每条记录都必须归属六类回归集之一")
    void shouldOnlyRecordKnownSuites() {
        RecordedBaseline baseline = RecordedBaseline.load();

        assertThat(baseline.entries()).isNotEmpty();
        assertThat(baseline.entries()).extracting(RecordedBaseline.Entry::suite)
                .allSatisfy(suite -> assertThat(RegressionDataset.SUITE_FILES).contains(suite));
        assertThat(baseline.passIds()).allSatisfy(id ->
                assertThat(baseline.entries()).extracting(RecordedBaseline.Entry::id).contains(id));
    }
}
