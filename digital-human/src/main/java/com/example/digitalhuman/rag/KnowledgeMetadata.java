package com.example.digitalhuman.rag;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;

/**
 * 元数据契约：写入侧必须带、检索侧必须查、命中后必须复核。
 *
 * <p>为什么把这件事做成契约而不是「记得写」：元数据漏写在写入时**毫无症状**，
 * 只有等用户提问、答案已经生成之后才暴露——典型的延迟暴露型缺陷。
 * 所以这里在写入后立刻回读校验，把线上事故降级成启动/调用时的显式失败。
 */
public final class KnowledgeMetadata {

    public static final String PROJECT_ID = "projectId";
    public static final String DOC_NAME = "docName";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String INGESTED_AT = "ingestedAt";

    private KnowledgeMetadata() {
    }

    /** 缺少任一必需字段直接拒绝入库，而不是静默通过。 */
    public static void requireContract(List<Document> chunks) {
        for (Document chunk : chunks) {
            Map<String, Object> metadata = chunk.getMetadata();
            for (String key : List.of(PROJECT_ID, DOC_NAME, CHUNK_INDEX)) {
                Object value = metadata.get(key);
                if (value == null || String.valueOf(value).isBlank()) {
                    throw new KnowledgeContractException(
                            "chunk 缺少必需元数据 " + key + "：" + chunk.getId());
                }
            }
        }
    }

    /**
     * 命中复核：隔离是「逻辑隔离」，所以每次检索都要确认返回的 chunk 真属于请求的项目。
     * 这不是多余的——过滤条件一旦写错，这里是唯一会喊出来的地方。
     */
    public static void requireOwnership(Long projectId, List<Document> hits) {
        String expected = String.valueOf(projectId);
        for (Document hit : hits) {
            Object actual = hit.getMetadata().get(PROJECT_ID);
            if (!expected.equals(String.valueOf(actual))) {
                throw new KnowledgeContractException(
                        "检索命中越界：请求项目 " + expected + "，命中 chunk 属于 " + actual + "（" + hit.getId() + "）");
            }
        }
    }

    public static String documentId(Long projectId, String docName, int chunkIndex) {
        return "p" + projectId + "-" + docName + "#" + chunkIndex;
    }
}
