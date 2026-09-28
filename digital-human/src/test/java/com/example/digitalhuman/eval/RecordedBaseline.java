package com.example.digitalhuman.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 一次真实评测运行的「答案快照」：把真实模型的输出录下来，供离线重放。
 *
 * <p>它解决一个具体问题：L4（离线回归评测）不该为了跑一轮判据就又调一遍模型——
 * 那样它既慢，又每次都换一批输入。把真实输出录成基线之后：
 * <ul>
 *   <li>改判据、改期望、加规则，都能在本地几秒内重放一遍，看有多少条结论被改变；</li>
 *   <li>{@code passIds} 是当时的结论基线，重放结果与它不一致就是「判据或数据集动了」，
 *       这类改动本来就该被显式看见（而不是悄悄把回归集改绿）。</li>
 * </ul>
 *
 * <p>快照是**证据不是真源**：它证明当时那批输出长什么样，不证明系统现在还是那样。
 * 真源永远是「模型 + 数据集 + 判据」三件套重新跑一次。
 */
public record RecordedBaseline(String datasetVersion,
                               String model,
                               String recordedAt,
                               List<String> passIds,
                               List<Entry> entries) {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** 一条录下来的运行事实。 */
    public record Entry(String id,
                        String suite,
                        String answer,
                        List<String> toolCalls,
                        Integer sourceCount,
                        Boolean refused,
                        Integer judgeScore) {

        public Entry {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public RunFacts toFacts() {
            return new RunFacts(answer, toolCalls, sourceCount, refused);
        }
    }

    public RecordedBaseline {
        passIds = passIds == null ? List.of() : List.copyOf(passIds);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static Path write(RecordedBaseline baseline, Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writeValueAsString(baseline), StandardCharsets.UTF_8);
            return file;
        } catch (IOException ex) {
            throw new UncheckedIOException("基线快照写入失败：" + file, ex);
        }
    }

    public static RecordedBaseline load() {
        String resource = "regression/recorded/answers.json";
        try (InputStream in = RecordedBaseline.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("基线快照缺失：" + resource
                        + "（先跑一轮 eval-live，再把 target/eval-runs/recorded-answers.json 复制过来）");
            }
            return MAPPER.readValue(in, RecordedBaseline.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取基线快照失败：" + resource, ex);
        }
    }
}
