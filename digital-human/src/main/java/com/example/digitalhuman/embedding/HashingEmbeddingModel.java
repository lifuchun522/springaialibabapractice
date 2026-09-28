package com.example.digitalhuman.embedding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 本地确定性向量化：**词法**哈希，不是语义 embedding。
 *
 * <p>为什么本仓库用它：这个环境里没有可用的向量化服务——DeepSeek 只提供对话模型，
 * 没有 embedding 接口（实测 `/models` 只返回两个 chat 模型），DashScope 又没有可用密钥。
 * 与其引一条「配了但没验证过」的通道（第 1 掌已经立过规矩：不留不可验证的通道），
 * 不如把链路做实、把局限写清楚。
 *
 * <p><b>它是什么</b>：中文字符二元组 + 英文单词做特征哈希，词频加权后 L2 归一化，
 * 于是「字面重叠多」的文本向量夹角就小。链路（写入→切分→向量化→存储→过滤检索→增强→引用）
 * 全都是真的，隔离与溯源也都是真的。
 *
 * <p><b>它不是**什么**：它不理解同义与改写（「多少钱」和「价格」在它眼里无关），
 * 所以它的召回率**不代表**生产水平。要换成真实模型，只需换掉这个 Bean
 * （DashScope text-embedding-v3 / OpenAI / 本地 ONNX），调用方一行都不用改——
 * 这正是 Spring AI 抽象要买到的东西，也是本掌能验证的部分。
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    /** 维度取 512：足够区分小规模知识库，也不至于让调试时的向量难以观察。 */
    public static final int DIMENSIONS = 512;

    private static final Pattern ASCII_WORD = Pattern.compile("[a-z0-9_]+");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fff]+");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(new Embedding(embed(texts.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (Map.Entry<String, Integer> feature : features(text).entrySet()) {
            int slot = Math.floorMod(feature.getKey().hashCode(), DIMENSIONS);
            vector[slot] += feature.getValue();
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /** 提取词法特征：中文按字符二元组（单字单独成组），英文按词。 */
    static Map<String, Integer> features(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        Map<String, Integer> counts = new LinkedHashMap<>();

        Matcher words = ASCII_WORD.matcher(lowered);
        while (words.find()) {
            counts.merge("w:" + words.group(), 1, Integer::sum);
        }

        Matcher runs = CJK_RUN.matcher(lowered);
        while (runs.find()) {
            String run = runs.group();
            if (run.length() == 1) {
                counts.merge("c:" + run, 1, Integer::sum);
                continue;
            }
            for (int i = 0; i + 1 < run.length(); i++) {
                counts.merge("c:" + run.substring(i, i + 2), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
