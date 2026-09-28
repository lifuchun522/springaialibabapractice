package com.example.digitalhuman.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 六类回归集的加载点。
 *
 * <p>文件名写死在这里而不是扫目录：回归集是资产，资产清单应该是显式的——
 * 扫目录的结果是「谁多放一个文件就悄悄多一类」，而显式清单会让漏掉的一类在契约用例里直接变红。
 */
public final class RegressionDataset {

    /** 六类回归集，顺序与文章里的表一致。 */
    public static final List<String> SUITE_FILES = List.of(
            "qa-basic",
            "project-knowledge",
            "tool-calling",
            "workflow",
            "multi-agent",
            "security-refusal");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RegressionDataset() {
    }

    public static List<RegressionSuite> loadAll() {
        List<RegressionSuite> suites = new ArrayList<>(SUITE_FILES.size());
        for (String file : SUITE_FILES) {
            suites.add(load(file));
        }
        return suites;
    }

    public static RegressionSuite load(String suite) {
        String resource = "regression/" + suite + ".json";
        try (InputStream in = RegressionDataset.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("回归集文件缺失：" + resource);
            }
            return MAPPER.readValue(in, RegressionSuite.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取回归集失败：" + resource, ex);
        }
    }

    /** 全部用例（带所属数据集标识，便于报告里直接写清来源）。 */
    public static List<CaseRef> allCases() {
        List<CaseRef> refs = new ArrayList<>();
        for (RegressionSuite suite : loadAll()) {
            for (RegressionCase item : suite.cases()) {
                refs.add(new CaseRef(suite.suite(), suite.version(), item));
            }
        }
        return refs;
    }

    public record CaseRef(String suite, String version, RegressionCase item) {
    }
}
