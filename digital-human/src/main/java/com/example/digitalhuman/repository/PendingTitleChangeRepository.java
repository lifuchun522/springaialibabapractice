package com.example.digitalhuman.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.digitalhuman.domain.PendingTitleChange;

public interface PendingTitleChangeRepository extends JpaRepository<PendingTitleChange, Long> {

    Optional<PendingTitleChange> findByConfirmToken(String confirmToken);

    List<PendingTitleChange> findByProjectIdAndStatusOrderByIdDesc(Long projectId, PendingTitleChange.Status status);
}
