package com.example.digitalhuman.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.digitalhuman.domain.ToolCallAudit;

public interface ToolCallAuditRepository extends JpaRepository<ToolCallAudit, Long> {

    List<ToolCallAudit> findByProjectIdOrderByIdDesc(Long projectId);
}
