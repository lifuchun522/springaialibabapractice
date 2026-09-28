package com.example.digitalhuman.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.digitalhuman.config.RagConfig;
import com.example.digitalhuman.rag.KnowledgeMetadata;

/**
 * 项目知识检索。
 *
 * <p>设计上最硬的一条：**{@code projectId} 是必填参数，且不提供无过滤的重载**。
 * 可以绕过的边界等于没有边界——只要有一个调用点忘了传过滤条件，
 * 整个系统的隔离性就退化成「取决于调用方自觉」。
 *
 * <p>第二条：**检索预算与展示条数分离**。`topK` 是「候选池要多大」（宁宽勿窄），
 * 展示条数才是「给模型看几条」。把 topK 当结果数用，是「加了过滤就一条都查不到」的常见根因。
 */
@Service
public class ProjectKnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(ProjectKnowledgeRetriever.class);

    private final VectorStore vectorStore;
    private final RagConfig.RagProperties properties;

    public ProjectKnowledgeRetriever(VectorStore vectorStore, RagConfig.RagProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<Document> retrieve(Long projectId, String question) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空：检索必须限定项目");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(properties.retrievalBudget())
                .similarityThreshold(properties.similarityThreshold())
                .filterExpression(KnowledgeMetadata.PROJECT_ID + " == '" + projectId + "'")
                .build());

        List<Document> safeHits = hits == null ? List.of() : hits;
        // 双保险：过滤条件一旦写错，这里是唯一会喊出来的地方
        KnowledgeMetadata.requireOwnership(projectId, safeHits);

        safeHits.forEach(hit -> log.info("[retrieve] projectId={} docName={} chunkIndex={} score={}",
                hit.getMetadata().get(KnowledgeMetadata.PROJECT_ID),
                hit.getMetadata().get(KnowledgeMetadata.DOC_NAME),
                hit.getMetadata().get(KnowledgeMetadata.CHUNK_INDEX),
                hit.getScore()));

        // 宽口径取候选、窄口径判定「有没有依据」：
        // 阈值 0 会让任何问题都拿到 topK 条（哪怕毫无关系），所以这里再按最低相关度筛一次；
        // 它是相关性判据，不是隔离手段——隔离只由上面的过滤条件保证。
        return safeHits.stream()
                .filter(hit -> hit.getScore() == null || hit.getScore() >= properties.minScore())
                .limit(properties.displayCount())
                .toList();
    }
}
