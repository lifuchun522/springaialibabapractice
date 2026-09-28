package com.example.digitalhuman.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 一次评测运行的可追溯记录。
 *
 * <p>「可追溯」在这里是可核对的四件事：<b>数据集版本、模型标识、运行时间、逐条明细</b>。
 * 缺任何一项，「这次比上次差」就只能靠印象争论——而评测的价值恰恰在于不用争。
 *
 * <p>记录落在 {@code target/eval-runs/}：它是运行产物，不进版本库；
 * 需要长期留存的运行记录由验收脚本挑出代表性的几份放进 {@code scripts/evidence-chNN/}。
 */
public record EvalRunRecord(String datasetVersion,
                           String model,
                           String startedAt,
                           String finishedAt,
                           Summary summary,
                           LlmJudge.CalibrationReport calibration,
                           List<CaseResult> results) {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public record Summary(int total, int live, int offlineReferenced, int passed, int failed, long elapsedMs) {
    }

    /**
     * 单条用例结果。
     *
     * @param outcome     PASS / FAIL / OFFLINE_REF
     * @param answer      被测系统的真实回答（原样保留，截断会让人无法复核判据）
     * @param sourceCount 知识问答路径召回到的来源条数；其它路径为 null
     * @param refused     知识问答路径是否走了系统拒答分支；其它路径为 null
     */
    public record CaseResult(String id,
                             String suite,
                             String mode,
                             String determinism,
                             String input,
                             String outcome,
                             String answer,
                             List<String> toolCalls,
                             Integer sourceCount,
                             Boolean refused,
                             List<String> ruleChecks,
                             List<String> failures,
                             Integer judgeScore,
                             String judgeReason,
                             long elapsedMs) {

        public CaseResult {
            toolCalls = List.copyOf(toolCalls);
            ruleChecks = List.copyOf(ruleChecks);
            failures = List.copyOf(failures);
        }
    }

    /** 落盘并返回文件路径；文件名带时间戳，覆盖不是问题，认错版本才是问题。 */
    public Path writeTo(Path directory, String stamp) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("eval-" + stamp + ".json");
            Files.writeString(file, MAPPER.writeValueAsString(this), StandardCharsets.UTF_8);

            Path tsv = directory.resolve("eval-" + stamp + ".tsv");
            StringBuilder sb = new StringBuilder("suite\tid\toutcome\tjudge\telapsedMs\tinput\n");
            for (CaseResult result : results) {
                sb.append(result.suite()).append('\t')
                        .append(result.id()).append('\t')
                        .append(result.outcome()).append('\t')
                        .append(result.judgeScore() == null ? "-" : result.judgeScore()).append('\t')
                        .append(result.elapsedMs()).append('\t')
                        .append(result.input().replaceAll("\\s+", " ")).append('\n');
            }
            Files.writeString(tsv, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException ex) {
            throw new UncheckedIOException("评测记录写入失败", ex);
        }
    }
}
