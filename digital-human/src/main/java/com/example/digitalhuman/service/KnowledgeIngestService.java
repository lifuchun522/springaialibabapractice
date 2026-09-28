package com.example.digitalhuman.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.digitalhuman.config.RagConfig;
import com.example.digitalhuman.domain.KnowledgeDocument;
import com.example.digitalhuman.rag.KnowledgeContractException;
import com.example.digitalhuman.rag.KnowledgeMetadata;
import com.example.digitalhuman.repository.KnowledgeDocumentRepository;

/**
 * 知识写入链路：Document → 切分 → 补元数据 → 向量化入库 → 回读校验 → 写真源表。
 *
 * <p>两个刻意的顺序：
 * <ol>
 *   <li><b>元数据在切分之后重建</b>：切块会拆出新 Document，不能假设 splitter 帮我带过去了；</li>
 *   <li><b>先入库再回读</b>：用过滤条件回查一次，查得到才算写成功。
 *       写入失败如果不校验，问题会推迟到用户提问时才暴露。</li>
 * </ol>
 */
@Service
public class KnowledgeIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final KnowledgeDocumentRepository documents;
    private final RagConfig.RagProperties properties;

    public KnowledgeIngestService(VectorStore vectorStore,
                                  TextSplitter textSplitter,
                                  KnowledgeDocumentRepository documents,
                                  RagConfig.RagProperties properties) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
        this.documents = documents;
        this.properties = properties;
    }

    @Transactional
    public int ingest(Long projectId, String docName, String content) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空：知识必须归属于某个项目");
        }
        String name = requireText(docName, "docName 不能为空");
        String text = requireText(content, "文档内容不能为空");

        List<Document> chunks = split(projectId, name, text);
        KnowledgeMetadata.requireContract(chunks);
        vectorStore.add(chunks);
        verifyReadBack(projectId, name, chunks.get(0));

        documents.findByProjectIdAndDocName(projectId, name)
                .ifPresentOrElse(existing -> existing.updateContent(text, chunks.size()),
                        () -> documents.save(new KnowledgeDocument(projectId, name, text, chunks.size())));

        log.info("知识入库 projectId={} docName={} chunks={}", projectId, name, chunks.size());
        return chunks.size();
    }

    /** 供启动重建与删除时复用：把一段文本切成带完整元数据的 chunk。 */
    public List<Document> split(Long projectId, String docName, String content) {
        Document source = Document.builder()
                .text(content)
                .metadata(Map.of(
                        KnowledgeMetadata.PROJECT_ID, String.valueOf(projectId),
                        KnowledgeMetadata.DOC_NAME, docName))
                .build();

        List<Document> split = textSplitter.split(source);
        List<Document> chunks = new ArrayList<>(split.size());
        for (int index = 0; index < split.size(); index++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(KnowledgeMetadata.PROJECT_ID, String.valueOf(projectId));
            metadata.put(KnowledgeMetadata.DOC_NAME, docName);
            metadata.put(KnowledgeMetadata.CHUNK_INDEX, index);
            metadata.put(KnowledgeMetadata.INGESTED_AT, Instant.now().toString());
            chunks.add(Document.builder()
                    .id(KnowledgeMetadata.documentId(projectId, docName, index))
                    .text(split.get(index).getText())
                    .metadata(metadata)
                    .build());
        }
        return chunks;
    }

    /** 回读校验：用项目过滤条件把这个 chunk 查回来，查不到就说明写入没生效。 */
    private void verifyReadBack(Long projectId, String docName, Document firstChunk) {
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(firstChunk.getText())
                .topK(properties.retrievalBudget())
                .similarityThreshold(0)
                .filterExpression(KnowledgeMetadata.PROJECT_ID + " == '" + projectId + "'")
                .build());

        boolean found = hits != null && hits.stream()
                .anyMatch(hit -> docName.equals(hit.getMetadata().get(KnowledgeMetadata.DOC_NAME)));
        if (!found) {
            throw new KnowledgeContractException(
                    "写入后回读校验失败：projectId=" + projectId + " docName=" + docName + " 没有检索到刚写入的 chunk");
        }
    }

    @Transactional
    public void delete(Long projectId, String docName) {
        KnowledgeDocument document = documents.findByProjectIdAndDocName(projectId, docName)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在：" + docName));

        List<String> ids = new ArrayList<>();
        for (int index = 0; index < document.getChunkCount(); index++) {
            ids.add(KnowledgeMetadata.documentId(projectId, docName, index));
        }
        vectorStore.delete(ids);
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list(Long projectId) {
        return documents.findByProjectIdOrderByIdAsc(projectId);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
