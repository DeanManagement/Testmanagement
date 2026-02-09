package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByKey(String key);

    Optional<Project> findByKey(String key);

    List<Project> findByKeyContainingIgnoreCase(String key);
}
