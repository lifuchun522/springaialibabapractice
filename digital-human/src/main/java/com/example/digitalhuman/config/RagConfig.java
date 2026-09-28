package com.example.digitalhuman.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.digitalhuman.embedding.HashingEmbeddingModel;

/**
 * RAG 装配点：切分器、向量化、向量库。
 *
 * <p>三个边界写在这里：
 * <ol>
 *   <li><b>切分之后再补元数据</b>：切块会把 Document 拆成多个 chunk，
 *       「我在 Document 上写了 projectId」和「每个 chunk 上都有 projectId」是两件事；</li>
 *   <li><b>向量化只看得见文本</b>：它完全不知道项目归属，所以隔离只能靠检索侧过滤；</li>
 *   <li><b>向量库是投影</b>：这里是内存实现，重启即丢；真源在 knowledge_document 表，
 *       启动时按真源重建索引（见 KnowledgeVectorReloader）。</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(RagConfig.RagProperties.class)
public class RagConfig {

    /**
     * @param retrievalBudget  检索候选池大小：先保证「正确 chunk 进得来」，再谈排序与展示
     * @param displayCount     真正喂给模型的条数
     * @param similarityThreshold 向量库的过滤阈值；0 表示不设阈值（中文短问句与长 chunk 的余弦相似度普遍不高，
     *                            阈值是「突然答不出来」的常见根因）
     * @param minScore         业务侧的「有没有依据」下限：低于它的命中不算依据。
     *                         它判的是**相关性**，不是隔离——用相关度去模拟隔离是方向性错误。
     * @param systemPrompt     有依据回答的人设与约束（提示词属于配置，不属于代码）
     */
    @ConfigurationProperties(prefix = "digital-human.rag")
    public record RagProperties(int retrievalBudget, int displayCount, double similarityThreshold,
                                double minScore, String systemPrompt) {

        public RagProperties {
            if (retrievalBudget <= 0) {
                retrievalBudget = 20;
            }
            if (displayCount <= 0) {
                displayCount = 4;
            }
            if (displayCount > retrievalBudget) {
                displayCount = retrievalBudget;
            }
            if (similarityThreshold < 0) {
                similarityThreshold = 0;
            }
            if (systemPrompt == null || systemPrompt.isBlank()) {
                systemPrompt = "你只能依据 <context> 中提供的资料回答。"
                        + "回答里必须用 [序号] 标注依据来自哪一条资料；"
                        + "如果资料里没有答案，直接说「资料里没有相关内容」，不要补充你自己的知识。";
            }
        }
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    TextSplitter textSplitter() {
        // chunkSize=300 字符、不重叠：中文 FAQ / 项目介绍这类短文档够用
        return TokenTextSplitter.builder()
                .withChunkSize(300)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }
}
