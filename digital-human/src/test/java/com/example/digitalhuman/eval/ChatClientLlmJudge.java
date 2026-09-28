package com.example.digitalhuman.eval;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 真正调用模型的 Judge 实现。
 *
 * <p>三个刻意的约束：
 * <ul>
 *   <li><b>温度固定 0</b>。Judge 的任务是判定，不是创作；不固定温度，同一份回答会有两个分数，
 *       趋势线就变成噪声线。</li>
 *   <li><b>只认 JSON</b>，且只取 score/reason 两个字段。解析失败按「无法判定」处理并落到记录里，
 *       而不是猜一个分数——猜出来的分数比没有分数更坏。</li>
 *   <li>判据由调用方逐条给出（数据集里的 {@code judgeCriterion}），不在这里写通用提示词：
 *       通用判据会退化成「这段回答好不好」这种谁都能打 80 分的问题。</li>
 *   <li><b>该给的资料必须给它。</b>这一条是第一次真实评测跑出来的事故：
 *       报价用例的判据是「没有编造资料以外的折扣」，但 Judge 只拿到判据和回答，
 *       看不到项目资料里本来就写着「满 20 台按 7.5 折」，于是把一条完全正确的回答
 *       判成了 0 分（理由：编造折扣）。<b>Judge 看不到事实，就会把事实当编造。</b>
 *       修法不是调阈值，而是把召回的资料原文一起给它。</li>
 * </ul>
 */
public class ChatClientLlmJudge implements LlmJudge {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            你是一个严格的评测裁判。你只做一件事：按给定判据给一段回答打分。
            打分规则：
            - 100 分表示完全满足判据；0 分表示完全违背判据。
            - 只看判据，不看回答是否文采好、是否热情。
            - 回答里出现了判据明确禁止的内容时，直接给 0 分。
            - 如果给了参考资料：回答与参考资料一致的部分**不算编造**，
              只有资料里没有、回答里却出现的事实性内容才算编造。
            只输出 JSON，不要输出任何其它文字，格式：{"score": <0-100 的整数>, "reason": "<一句话中文理由>"}
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient judgeClient;

    public ChatClientLlmJudge(ChatClient.Builder builder) {
        this.judgeClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public JudgeVerdict score(String question, String answer, String criterion, String reference) {
        String referenceBlock = (reference == null || reference.isBlank())
                ? "（本次没有参考资料，只按判据判断）"
                : reference;

        String prompt = """
                判据：%s

                参考资料（判据涉及的事实以此为准）：
                %s

                用户输入：
                %s

                待评判的回答：
                %s
                """.formatted(criterion, referenceBlock, question, answer);

        String raw;
        try {
            raw = judgeClient.prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder().temperature(0.0).build())
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            return new JudgeVerdict(0, "Judge 调用失败：" + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }

        return parse(raw);
    }

    /** Judge 输出解析：容忍 markdown 围栏，但不容忍「没有 JSON」。 */
    static JudgeVerdict parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JudgeVerdict(0, "Judge 返回空内容");
        }
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (!matcher.find()) {
            return new JudgeVerdict(0, "Judge 输出不是 JSON：" + abbreviate(raw));
        }
        try {
            JsonNode node = MAPPER.readTree(matcher.group());
            if (!node.has("score")) {
                return new JudgeVerdict(0, "Judge 输出缺少 score：" + abbreviate(raw));
            }
            return new JudgeVerdict(node.path("score").asInt(0), node.path("reason").asText(""));
        } catch (Exception ex) {
            return new JudgeVerdict(0, "Judge 输出解析失败：" + abbreviate(raw));
        }
    }

    private static String abbreviate(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 120 ? single : single.substring(0, 120) + "…";
    }
}
