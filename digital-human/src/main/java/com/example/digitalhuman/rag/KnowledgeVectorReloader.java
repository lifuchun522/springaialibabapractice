package com.example.digitalhuman.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.repository.KnowledgeDocumentRepository;
import com.example.digitalhuman.service.KnowledgeIngestService;

/**
 * 启动时按真源重建向量索引。
 *
 * <p>为什么需要它：本仓库用的是内存向量库（`SimpleVectorStore`），重启即空、多实例各存一份。
 * 文章把「重启即丢」列为 RAG 的遗留问题之一——本仓库的处置不是假装看不见，
 * 而是把**真源放在关系库**（`knowledge_document`），让向量库成为可重建的投影：
 * 换向量库、换 embedding、扩容重建索引，业务数据都不受影响。
 *
 * <p>生产环境仍然应该换成持久化向量库（PGVector / Redis / AnalyticDB 等），
 * 并且逐库核验过滤语义是否一致——这是文章明确要求核验的一点。
 */
@Component
public class KnowledgeVectorReloader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorReloader.class);

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeIngestService ingestService;
    private final VectorStore vectorStore;

    public KnowledgeVectorReloader(KnowledgeDocumentRepository documents,
                                   KnowledgeIngestService ingestService,
                                   VectorStore vectorStore) {
        this.documents = documents;
        this.ingestService = ingestService;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        var all = documents.findAll();
        if (all.isEmpty()) {
            log.info("知识库为空：没有需要重建的向量索引");
            return;
        }

        int chunks = 0;
        for (var document : all) {
            var split = ingestService.split(document.getProjectId(), document.getDocName(), document.getContent());
            KnowledgeMetadata.requireContract(split);
            vectorStore.add(split);
            chunks += split.size();
        }
        log.info("向量索引重建完成：文档 {} 篇，chunk {} 个（真源：knowledge_document）", all.size(), chunks);
    }
}
