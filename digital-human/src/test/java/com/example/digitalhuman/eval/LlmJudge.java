package com.example.digitalhuman.eval;

import java.util.List;

/**
 * LLM Judge：给「语义质量」这类规则判不了的用例打分。
 *
 * <p>两件事必须写在前面，否则这一层迟早会变成事故源：
 * <ul>
 *   <li><b>Judge 自己也是非确定的。</b>它能用，但它的分数在未经校准前不许当成准确率对外报；
 *       校准方法是拿一小批人工标注样本对照 Judge 判断，看方向是否一致
 *       （见 {@link JudgeCalibration}）。</li>
 *   <li><b>Judge 不进 CI 阻断路径。</b>它只产出趋势与告警，规则能判的部分才阻断合并。</li>
 * </ul>
 */
public interface LlmJudge {



    /**
     * @param question  用例输入
     * @param answer    被测系统的真实回答
     * @param criterion 判据原文（数据集里逐条写清楚，改判据就是改资产）
     * @param reference 判据涉及的资料原文（可为空）；为空表示这条判据不依赖外部事实
     * @return 0~100 分与理由
     */
    JudgeVerdict score(String question, String answer, String criterion, String reference);

    /**
     * 不带资料的判据走这个重载。
     *
     * <p>默认实现是刻意留的：{@code reference} 一旦变成必填，作者就会顺手塞一段
     * 「看起来像资料」的文字进来，而 Judge 的分数会因此变得不可归因。
     */
    default JudgeVerdict score(String question, String answer, String criterion) {
        return score(question, answer, criterion, "");
    }

    /**
     * @param score 0~100
     * @param reason 一句话理由，进运行时记录
     */
    record JudgeVerdict(int score, String reason) {

        public JudgeVerdict {
            score = Math.max(0, Math.min(100, score));
            reason = reason == null ? "" : reason;
        }

        public boolean accepts(int threshold) {
            return score >= threshold;
        }
    }

    /** 校准结果：Judge 与人工标注的一致率。 */
    record CalibrationReport(int samples, int agreed, List<String> disagreements) {

        public CalibrationReport {
            disagreements = List.copyOf(disagreements);
        }

        /** 一致率；无一例一致时为 0，不返回 NaN。 */
        public double agreement() {
            return samples == 0 ? 0d : (double) agreed / samples;
        }
    }
}
