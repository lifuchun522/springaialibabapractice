-- 第 8 掌：知识库的真源在关系库里，向量库只是它的一个「投影」。
-- 这么分的原因很直接：向量库（SimpleVectorStore）是内存实现，重启即丢、多实例各存一份；
-- 而「哪个项目有哪些文档」是业务事实，必须能查得回、能重建索引。
--
-- 另一条约束写在这里：projectId 是知识隔离的唯一锚点，它必须在写入时就固化，
-- 而不是等检索时再想办法区分——向量相似度里没有任何「项目归属」信息。

CREATE TABLE knowledge_document (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    doc_name    VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,
    chunk_count INT          NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document_project_name (project_id, doc_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
