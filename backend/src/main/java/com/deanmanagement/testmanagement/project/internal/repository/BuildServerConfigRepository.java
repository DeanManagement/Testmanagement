package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BuildServerConfigRepository extends JpaRepository<BuildServerConfig, UUID> {

    Optional<BuildServerConfig> findByName(String name);

    boolean existsByActiveTrue();
}
