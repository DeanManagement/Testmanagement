package com.deanmanagement.testmanagement.project.internal.mcp;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface McpToolInvocationRepository extends JpaRepository<McpToolInvocation, UUID> {

    Page<McpToolInvocation> findByApiKeyIdOrderByCreatedAtDesc(UUID apiKeyId, Pageable pageable);

    Page<McpToolInvocation> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Page<McpToolInvocation> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM McpToolInvocation i WHERE i.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
