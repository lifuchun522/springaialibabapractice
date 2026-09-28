package com.example.digitalhuman.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Judge 校准：用一小批**人工标注**的样本对照 Judge 的判断。
 *
 * <p>为什么必须有这一步：Judge 本身非确定，未经校准就把它的分数当准确率对外报，
 * 是在拿噪声当指标。校准不追求 Judge 与人不差分毫，只要求**方向一致**——
 * 人判通过的回答，Judge 也给通过；人判该拒答的越权回答，Judge 也必须不给分。
 *
 * <p>样本量按项目实际情况定，本仓库先放少量样本把机制跑通：机制通了，样本可以慢慢加；
 * 机制不通，加多少样本都是在给一个无人核对的分数量表添刻度。
 *
 * <p><b>校准集必须自带事实来源</b>（{@code reference}）。第一次校准就栽在这里：
 * cal-001 的回答与项目资料完全一致，判据是「没有编造资料以外的折扣」，
 * 但样本里没给资料，Judge 于是把正确回答判成「编造」。校准如果建立在样本自身的缺陷上，
 * 得到的不是「Judge 准不准」，而是「样本全不全」——两者结论完全相反。
 */
public final class JudgeCalibration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JudgeCalibration() {
    }

    /** 一条人工标注样本。 */
    public record Sample(String id, String question, String answer, String criterion,
                         String humanLabel, String reference, String note) {

        public static final String PASS = "PASS";
        public static final String FAIL = "FAIL";

        public boolean humanAccepts() {
            return PASS.equalsIgnoreCase(humanLabel);
        }
    }

    /** 校准集文件。 */
    public record CalibrationSet(int threshold, List<Sample> samples) {

        public CalibrationSet {
            samples = samples == null ? List.of() : List.copyOf(samples);
        }
    }

    public static CalibrationSet load() {
        String resource = "regression/judge-calibration.json";
        try (InputStream in = JudgeCalibration.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("校准集文件缺失：" + resource);
            }
            return MAPPER.readValue(in, CalibrationSet.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取校准集失败：" + resource, ex);
        }
    }

    /**
     * 跑一遍校准。
     *
     * @param judge Judge 实现（真实模型或桩）
     * @param set   人工标注样本
     */
    public static LlmJudge.CalibrationReport run(LlmJudge judge, CalibrationSet set) {
        int agreed = 0;
        List<String> disagreements = new ArrayList<>();
        for (Sample sample : set.samples()) {
            LlmJudge.JudgeVerdict verdict = judge.score(
                    sample.question(), sample.answer(), sample.criterion(), sample.reference());
            boolean judgeAccepts = verdict.accepts(set.threshold());
            if (judgeAccepts == sample.humanAccepts()) {
                agreed++;
            } else {
                disagreements.add("%s 人工=%s Judge=%d(%s) 备注=%s".formatted(
                        sample.id(), sample.humanLabel(), verdict.score(),
                        abbreviate(verdict.reason()), sample.note()));
            }
        }
        return new LlmJudge.CalibrationReport(set.samples().size(), agreed, disagreements);
    }

    private static String abbreviate(String text) {
        String single = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return single.length() <= 60 ? single : single.substring(0, 60) + "…";
    }
}
