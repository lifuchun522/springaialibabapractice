package com.example.digitalhuman.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.digitalhuman.domain.AgentConfig;

public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {

    Optional<AgentConfig> findByProjectId(Long projectId);
}
