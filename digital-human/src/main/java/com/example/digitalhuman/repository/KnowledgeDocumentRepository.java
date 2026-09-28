package com.example.digitalhuman.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.digitalhuman.domain.KnowledgeDocument;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<KnowledgeDocument> findByProjectIdAndDocName(Long projectId, String docName);

    void deleteByProjectIdAndDocName(Long projectId, String docName);
}
