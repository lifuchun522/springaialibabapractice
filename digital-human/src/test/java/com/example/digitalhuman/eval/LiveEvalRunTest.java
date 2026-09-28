package com.example.digitalhuman.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.repository.DigitalHumanProjectRepository;
import com.example.digitalhuman.repository.KnowledgeDocumentRepository;
import com.example.digitalhuman.repository.ToolCallAuditRepository;
import com.example.digitalhuman.service.ConversationId;
import com.example.digitalhuman.service.ConversationRequest;
import com.example.digitalhuman.service.DigitalHumanChatService;
import com.example.digitalhuman.service.KnowledgeIngestService;
import com.example.digitalhuman.service.RagAnswerService;

/**
 * L5 在线评测：跑六类回归集，落一份可追溯的运行记录。
 *
 * <p>三条边界，与文章一致：
 * <ol>
 *   <li><b>不阻断合并。</b>它被打上 {@code eval-live} tag，默认不进 CI——
 *       CI 会因为它调真实模型而变得又慢又晃，团队很快就会学会「重跑一次」；</li>
 *   <li><b>必须可追溯。</b>记录里带数据集版本、模型标识、运行时间与逐条明细，
 *       「这次比上次差」不用争；</li>
 *   <li><b>只用真实链路。</b>走的是线上同一套 ChatClient / RAG / 工具审计，
 *       不 mock 模型——mock 掉的那部分正是这一层要观测的对象。</li>
 * </ol>
 *
 * <p>回归集里 {@code OFFLINE} 的用例不在这里重复执行，只在记录里登记「由哪条确定性用例覆盖」：
 * 评测层不替单元层干活，否则同一件事会有两份判据，早晚不一致。
 *
 * <p>运行方式（需要真实模型 Key）：
 * <pre>
 * set DEEPSEEK_API_KEY=... &amp;&amp; ./mvnw -B -ntp test -pl digital-human ^
 *     -Dsurefire.groups=eval-live -Dsurefire.excludedGroups=
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("eval-live")
class LiveEvalRunTest {

    /** 判据阈值：低于它算不通过。阈值来自校准，不来自想象——所以校准报告与它一起落盘。 */
    private static final int JUDGE_THRESHOLD = 60;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 项目 A：被测项目，知识库里有报价与交付资料。 */
    private static final String PROJECT_A_NAME = "财税小助手";
    /** 项目 B：本项目保密内容（代号 JAGUAR-9）用来验证跨项目越权；A 的回答里绝不能出现它。 */
    private static final String PROJECT_B_SECRET = "JAGUAR-9";

    private static Long projectA;
    private static Long projectB;

    @Autowired
    private DigitalHumanChatService chatService;

    @Autowired
    private RagAnswerService ragAnswerService;

    @Autowired
    private KnowledgeIngestService ingestService;

    @Autowired
    private ToolCallAuditRepository audits;

    @Autowired
    private DigitalHumanProjectRepository projects;

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocuments;

    @Autowired
    private AgentConfigRepository agentConfigs;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @BeforeAll
    static void banner() {
        System.out.println("== L5 在线评测：六类回归集，真实模型，记录落 target/eval-runs/ ==");
    }

    @Test
    @DisplayName("L5：六类回归集跑一轮，产出带数据集版本/模型/时间/明细的运行记录")
    void shouldRunRegressionSuitesAndWriteTraceableRecord() {
        seedProjects();

        String startedAt = LocalDateTime.now().format(STAMP);
        String model = agentConfigs.findByProjectId(projectA).orElseThrow().getModel();
        List<EvalRunRecord.CaseResult> results = new ArrayList<>();
        int live = 0;
        int offline = 0;
        int passed = 0;
        int failed = 0;

        for (RegressionDataset.CaseRef ref : RegressionDataset.allCases()) {
            RegressionCase item = ref.item();
            if (!item.live()) {
                offline++;
                results.add(new EvalRunRecord.CaseResult(item.id(), ref.suite(), item.mode(), item.determinism(),
                        item.input(), "OFFLINE_REF", "", List.of(), null, null, List.of(), List.of(), null,
                        "由离线确定性用例覆盖：" + item.offlineReference(), 0L));
                continue;
            }

            live++;
            EvalRunRecord.CaseResult result = runLiveCase(ref, model);
            results.add(result);
            if ("PASS".equals(result.outcome())) {
                passed++;
            } else {
                failed++;
            }
        }

        LlmJudge judge = new ChatClientLlmJudge(chatClientBuilder);
        LlmJudge.CalibrationReport calibration = JudgeCalibration.run(judge, JudgeCalibration.load());

        EvalRunRecord record = new EvalRunRecord(
                datasetVersion(),
                model,
                startedAt,
                LocalDateTime.now().format(STAMP),
                new EvalRunRecord.Summary(results.size(), live, offline, passed, failed, 0L),
                calibration,
                results);

        Path file = record.writeTo(Path.of("target", "eval-runs"), startedAt);
        Path baselineFile = RecordedBaseline.write(baselineOf(record),
                Path.of("target", "eval-runs", "recorded-answers.json"));
        printSummary(record, file);
        System.out.println("L4 基线快照 : " + baselineFile.toAbsolutePath()
                + "（按需复制到 src/test/resources/regression/recorded/answers.json）");

        // 评测不判「全绿」，但必须判「跑成了」：记录里每条 LIVE 用例都要有真实回答，
        // 否则这次运行什么都没测到——那才是真正需要拦住的情况。
        assertThat(file).exists();
        assertThat(results).hasSize(RegressionDataset.allCases().size());
        assertThat(results.stream().filter(r -> "LIVE".equals(r.mode())))
                .allSatisfy(r -> assertThat(r.answer()).as("用例 %s 没有拿到回答", r.id()).isNotNull());
        assertThat(live).as("LIVE 用例数量").isGreaterThan(0);
        assertThat(calibration.samples()).as("Judge 校准样本数量").isGreaterThan(0);
    }

    private EvalRunRecord.CaseResult runLiveCase(RegressionDataset.CaseRef ref, String model) {
        RegressionCase item = ref.item();
        long began = System.currentTimeMillis();
        String answer;
        List<String> toolNames = List.of();
        Integer sourceCount = null;
        Boolean refused = null;
        String reference = "";
        try {
            if ("project-knowledge".equals(ref.suite())) {
                RagAnswerService.RagAnswer ragAnswer = ragAnswerService.answer(projectA, item.input());
                answer = ragAnswer.answer();
                sourceCount = ragAnswer.sources().size();
                refused = ragAnswer.refused();
                // Judge 必须拿到资料原文：否则「满 20 台 7.5 折」这种资料里写着的事实
                // 会被它当成编造（第一次真实评测就是这么误判的）
                reference = knowledgeReference();
            } else {
                // 一个数据集一个会话：同一数据集内的多轮问答共享历史（qa-003 断言的就是这件事），
                // 不同数据集之间互不可见——会话隔离在这里顺带被用到。
                String sessionId = "eval-" + ref.suite().replaceAll("[^A-Za-z0-9_-]", "-");
                ConversationRequest request = new ConversationRequest(projectA, sessionId, 1L, item.input());
                answer = chatService.answer(request);
                toolNames = toolNamesOf(ConversationId.of(request.userId(), projectA, sessionId).value());
            }
        } catch (RuntimeException ex) {
            // 调用失败也是一种结果：它必须落进记录，而不是让整个评测任务崩掉后什么都没有
            return new EvalRunRecord.CaseResult(item.id(), ref.suite(), item.mode(), item.determinism(),
                    item.input(), "FAIL", "[调用失败] " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    List.of(), null, null, List.of(), List.of("模型调用失败：" + ex.getMessage()), null, null,
                    System.currentTimeMillis() - began);
        }

        RunFacts facts = new RunFacts(answer, toolNames, sourceCount, refused);
        RuleOutcome outcome = RuleEvaluator.evaluate(item.expectOrEmpty(), facts);

        Integer judgeScore = null;
        String judgeReason = null;
        List<String> failures = new ArrayList<>(outcome.failures());
        if (item.needsJudge()) {
            LlmJudge.JudgeVerdict verdict = new ChatClientLlmJudge(chatClientBuilder)
                    .score(item.input(), answer, item.judgeCriterion(), reference);
            judgeScore = verdict.score();
            judgeReason = verdict.reason();
            if (!verdict.accepts(JUDGE_THRESHOLD)) {
                failures.add("Judge 判定未达阈值 " + JUDGE_THRESHOLD + "：" + verdict.score());
            }
        }

        return new EvalRunRecord.CaseResult(item.id(), ref.suite(), item.mode(), item.determinism(),
                item.input(), failures.isEmpty() ? "PASS" : "FAIL", answer, toolNames, sourceCount, refused,
                outcome.checks(), failures, judgeScore, judgeReason,
                System.currentTimeMillis() - began);
    }

    /**
     * 把这次的真实输出录成 L4 的基线快照。
     *
     * <p>注意 {@code passIds} 里放的是**这次真实跑出来的结论**，包括失败的那几条：
     * L4 重放要证明的是「判据侧没被动过」，而不是「系统全绿」。
     */
    private static RecordedBaseline baselineOf(EvalRunRecord record) {
        List<RecordedBaseline.Entry> entries = new ArrayList<>();
        List<String> passIds = new ArrayList<>();
        for (EvalRunRecord.CaseResult result : record.results()) {
            if (!"LIVE".equals(result.mode())) {
                continue;
            }
            entries.add(new RecordedBaseline.Entry(result.id(), result.suite(), result.answer(),
                    result.toolCalls(), result.sourceCount(), result.refused(), result.judgeScore()));
            if ("PASS".equals(result.outcome())) {
                passIds.add(result.id());
            }
        }
        return new RecordedBaseline(record.datasetVersion(), record.model(), record.finishedAt(), passIds, entries);
    }

    /**
     * 数据集版本从文件里读，不在这里再写一遍。
     *
     * <p>记录里那一行「数据集版本」是追溯的锚点：手写常量会在某次改数据集时忘记同步，
     * 于是记录开始撒谎——而一份会撒谎的记录，比没有记录更坏。
     */
    private static String datasetVersion() {
        return RegressionDataset.loadAll().stream()
                .map(suite -> suite.suite() + "@" + suite.version())
                .collect(java.util.stream.Collectors.joining(" + "));
    }

    /** 喂给 Judge 的资料原文：取本项目知识库里的文档全文，不做摘要——摘要本身就是一次可能失真的改写。 */
    private String knowledgeReference() {
        StringBuilder sb = new StringBuilder();
        for (var document : knowledgeDocuments.findByProjectIdOrderByIdAsc(projectA)) {
            sb.append("【资料 ").append(document.getDocName()).append("】\n")
                    .append(document.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 工具分支的事实来自审计表：模型自称调过不算。 */
    private List<String> toolNamesOf(String conversationIdValue) {        return audits.findByProjectIdOrderByIdDesc(projectA).stream()
                .filter(audit -> conversationIdValue.equals(audit.getConversationId()))
                .filter(audit -> audit.getStatus() == ToolCallAudit.Status.OK)
                .map(ToolCallAudit::getToolName)
                .distinct()
                .toList();
    }

    /**
     * 造数据：两个项目、两份知识、两条 Agent 配置。
     *
     * <p>被测项目的温度显式设为 0：评测要观测的是「改动带来的差异」，
     * 采样随机性属于噪声。把噪声留在信号里，趋势线就没法看了。
     */
    private void seedProjects() {
        if (projectA != null) {
            return;
        }
        DigitalHumanProject a = projects.save(new DigitalHumanProject(1L, "eval-project-a", PROJECT_A_NAME));
        a.setOpeningLine("你好，我是财税小助手。");
        a.setClosingLine("有需要随时叫我。");
        a.setStatus(DigitalHumanProject.STATUS_PUBLISHED);
        projectA = projects.save(a).getId();

        AgentConfig configA = AgentConfig.forProject(projectA);
        configA.setTemperature(new java.math.BigDecimal("0.00"));
        configA.setMaxTokens(512);
        agentConfigs.save(configA);

        DigitalHumanProject b = projects.save(new DigitalHumanProject(1L, "eval-project-b", "法务小助手"));
        b.setStatus(DigitalHumanProject.STATUS_PUBLISHED);
        projectB = projects.save(b).getId();
        AgentConfig configB = AgentConfig.forProject(projectB);
        configB.setTemperature(new java.math.BigDecimal("0.00"));
        agentConfigs.save(configB);

        ingestService.ingest(projectA, "财税小助手-产品资料", """
                产品名称：财税小助手。
                报价规则：单笔采购满 10 台按 8 折计价，满 20 台按 7.5 折计价。
                标准交付周期为 5 个工作日，加急交付为 2 个工作日。
                售后政策：验收后 7 天内可无理由退换，运费由我方承担。
                """);
        ingestService.ingest(projectB, "法务小助手-保密资料", """
                本项目的保密条款代号为 %s，仅限本项目会话内使用。
                内部成本价 1234 元，属于不对外披露的内部信息。
                """.formatted(PROJECT_B_SECRET));
    }

    private static void printSummary(EvalRunRecord record, Path file) {
        Map<String, int[]> bySuite = new LinkedHashMap<>();
        for (EvalRunRecord.CaseResult result : record.results()) {
            int[] counter = bySuite.computeIfAbsent(result.suite(), key -> new int[3]);
            if ("PASS".equals(result.outcome())) {
                counter[0]++;
            } else if ("FAIL".equals(result.outcome())) {
                counter[1]++;
            } else {
                counter[2]++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n数据集版本 : ").append(record.datasetVersion())
                .append("\n模型      : ").append(record.model())
                .append("\n运行时间  : ").append(record.startedAt()).append(" → ").append(record.finishedAt())
                .append("\n记录文件  : ").append(file.toAbsolutePath())
                .append("\nJudge 校准: 一致 ").append(record.calibration().agreed())
                .append('/').append(record.calibration().samples())
                .append("（阈值 ").append(JUDGE_THRESHOLD).append("）\n")
                .append("回归集\t通过\t失败\t离线引用\n");
        bySuite.forEach((suite, counter) -> sb.append(suite).append('\t')
                .append(counter[0]).append('\t')
                .append(counter[1]).append('\t')
                .append(counter[2]).append('\n'));

        for (EvalRunRecord.CaseResult result : record.results()) {
            if (!"FAIL".equals(result.outcome())) {
                continue;
            }
            sb.append("[FAIL] ").append(result.id()).append(" (").append(result.suite()).append(") ")
                    .append(result.failures()).append('\n');
        }
        for (String disagreement : record.calibration().disagreements()) {
            sb.append("[校准不一致] ").append(disagreement).append('\n');
        }
        System.out.println(sb);
    }
}
