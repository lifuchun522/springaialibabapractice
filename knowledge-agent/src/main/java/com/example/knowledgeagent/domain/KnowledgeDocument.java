package com.example.knowledgeagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** 知识 Agent 自己的文档表：主服务没有这张表，也没有它的实体类（验收第一条）。 */
@Entity
@Table(name = "knowledge_agent_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_name", nullable = false, length = 255)
    private String docName;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String docName, String content) {
        this.docName = docName;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public String getDocName() {
        return docName;
    }

    public String getContent() {
        return content;
    }
}
