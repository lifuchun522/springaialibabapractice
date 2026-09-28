package com.example.knowledgeagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.knowledgeagent.domain.KnowledgeDocument;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
}
