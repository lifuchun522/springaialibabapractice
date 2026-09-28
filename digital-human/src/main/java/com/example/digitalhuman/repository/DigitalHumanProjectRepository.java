package com.example.digitalhuman.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.digitalhuman.domain.DigitalHumanProject;

public interface DigitalHumanProjectRepository extends JpaRepository<DigitalHumanProject, Long> {

    List<DigitalHumanProject> findByOwnerIdOrderByIdDesc(Long ownerId);

    Optional<DigitalHumanProject> findByIdAndOwnerId(Long id, Long ownerId);
}
