package com.example.digitalhuman.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.digitalhuman.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 产品历史按 projectId + sessionId 查，与模型看到的 Memory 窗口互不相干。 */
    List<ChatMessage> findByProjectIdAndSessionIdOrderByIdAsc(Long projectId, String sessionId);
}
