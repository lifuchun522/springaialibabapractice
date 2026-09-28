package com.example.digitalhuman.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1：评测引擎自己必须是确定性的。
 *
 * <p>这套用例的存在理由很直接：如果规则判定器本身会误判，
 * 那么「评测结果下降」既可能是被测系统退化，也可能是判据坏了——而后者更难发现，
 * 因为它安静、且出现在最需要信任报告的时刻。
 */
class RuleEvaluatorTest {

    @Test
    @DisplayName("规则命中容忍空白与大小写差异，但不放过真实的缺失")
    void shouldTolerateOnlyWhitespaceAndCase() {
        Expect expect = new Expect(List.of("8 折"), null, null, null, null, null, null, null);

        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("依据 [1]，满 10 台按 8折 计价。")).passed())
                .as("8折 与 8 折 是排版差异，不该判失败")
                .isTrue();
        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("依据 [1]，满 10 台按九折计价。")).passed())
                .isFalse();
    }

    @Test
    @DisplayName("禁止内容一旦出现即硬失败，哪怕回答里也有拒答话术")
    void shouldFailOnForbiddenContent() {
        Expect expect = new Expect(null, List.of("JAGUAR-9"), null, null, Boolean.TRUE, null, null, null);

        RuleOutcome outcome = RuleEvaluator.evaluate(expect,
                RunFacts.ofAnswer("抱歉，我不能提供……不过代号是 JAGUAR-9。"));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.failures()).anySatisfy(failure ->
                assertThat(failure).contains("JAGUAR-9"));
    }

    @Test
    @DisplayName("拒答意图只认显式表达：含糊其辞不算拒答")
    void shouldRequireExplicitRefusalIntent() {
        Expect expect = new Expect(null, null, null, null, Boolean.TRUE, null, null, null);

        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("这个项目的知识库里没有相关内容，我不能凭猜测回答。")).passed())
                .isTrue();
        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("嗯……这个嘛，我们之后再聊。")).passed())
                .isFalse();
    }

    @Test
    @DisplayName("工具分支断言取真实审计记录，不取模型自称")
    void shouldAssertToolsFromAuditFacts() {
        Expect expect = new Expect(null, null, null, null, null, null, null, List.of("getProjectInfo"));

        assertThat(RuleEvaluator.evaluate(expect,
                new RunFacts("我已经查过了。", List.of("getProjectInfo"), null, null)).passed())
                .isTrue();
        assertThat(RuleEvaluator.evaluate(expect,
                new RunFacts("我调用了 getProjectInfo 工具。", List.of(), null, null)).passed())
                .as("模型说自己调了不算：审计表里没有就是没有")
                .isFalse();
    }

    @Test
    @DisplayName("知识问答的路径断言：必须有来源，且无依据时必须由系统拒答")
    void shouldAssertRetrievalAndRefusalPaths() {
        Expect withSources = new Expect(null, null, null, null, null, Boolean.TRUE, Boolean.FALSE, null);
        assertThat(RuleEvaluator.evaluate(withSources,
                new RunFacts("依据 [1]……", List.of(), 3, false)).passed()).isTrue();
        assertThat(RuleEvaluator.evaluate(withSources,
                new RunFacts("这个项目的知识库里没有相关内容。", List.of(), 0, true)).passed())
                .as("召回为空说明依据没进上下文，这条用例必须红")
                .isFalse();

        Expect noBasis = new Expect(null, null, null, null, null, null, Boolean.TRUE, null);
        assertThat(RuleEvaluator.evaluate(noBasis,
                new RunFacts("这个项目的知识库里没有相关内容。", List.of(), 0, true)).passed()).isTrue();
        assertThat(RuleEvaluator.evaluate(noBasis,
                new RunFacts("港股代码应该是 00700。", List.of(), 0, false)).passed())
                .as("无依据却没走拒答分支 = 系统行为退化")
                .isFalse();
    }

    @Test
    @DisplayName("长度上下界都生效")
    void shouldCheckLengthBounds() {
        Expect expect = new Expect(null, null, 5, 10, null, null, null, null);

        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("1234")).passed()).isFalse();
        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("12345678901")).passed()).isFalse();
        assertThat(RuleEvaluator.evaluate(expect, RunFacts.ofAnswer("1234567")).passed()).isTrue();
    }

    @Test
    @DisplayName("Judge 输出解析：只认 JSON，解析失败按无法判定处理而不是猜一个分数")
    void shouldParseJudgeOutputStrictly() {
        assertThat(ChatClientLlmJudge.parse("{\"score\": 85, \"reason\": \"依据完整\"}"))
                .isEqualTo(new LlmJudge.JudgeVerdict(85, "依据完整"));
        assertThat(ChatClientLlmJudge.parse("```json\n{\"score\": 0, \"reason\": \"泄露原文\"}\n```").score())
                .isZero();
        assertThat(ChatClientLlmJudge.parse("这段回答看起来不错，我给 90 分。").score())
                .as("没有 JSON 就不是可用的判定")
                .isZero();
        assertThat(ChatClientLlmJudge.parse("{\"score\": 150, \"reason\": \"越界\"}").score())
                .as("超界分数被夹到 0~100")
                .isEqualTo(100);
        assertThat(ChatClientLlmJudge.parse(null).reason()).isNotBlank();
    }

    @Test
    @DisplayName("校准集本身必须成对出现：通过样本与失败样本都要有")
    void shouldHaveCalibrationSamplesOfBothLabels() {
        JudgeCalibration.CalibrationSet set = JudgeCalibration.load();

        assertThat(set.samples()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(set.samples()).extracting(JudgeCalibration.Sample::humanLabel)
                .contains(JudgeCalibration.Sample.PASS, JudgeCalibration.Sample.FAIL);
        assertThat(set.threshold()).isBetween(1, 100);
    }

    @Test
    @DisplayName("校准报告算的是一致率：方向一致才算一致")
    void shouldComputeAgreement() {
        JudgeCalibration.CalibrationSet set = new JudgeCalibration.CalibrationSet(60, List.of(
                new JudgeCalibration.Sample("s1", "q", "a", "c", "PASS", "", "对"),
                new JudgeCalibration.Sample("s2", "q", "a", "c", "FAIL", "", "错")));

        LlmJudge alwaysPass = (question, answer, criterion, reference) -> new LlmJudge.JudgeVerdict(90, "总是通过");
        LlmJudge.CalibrationReport report = JudgeCalibration.run(alwaysPass, set);

        assertThat(report.samples()).isEqualTo(2);
        assertThat(report.agreed()).isEqualTo(1);
        assertThat(report.agreement()).isEqualTo(0.5d);
        assertThat(report.disagreements()).hasSize(1);
    }
}
