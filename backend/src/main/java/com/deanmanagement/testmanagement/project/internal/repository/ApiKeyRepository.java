package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findAllByOrderByCreatedAtDesc();

    /** Legacy/global keys (PRD-021 §4.2) — surfaced in the startup deprecation warning. */
    List<ApiKey> findByProjectIsNullAndRevokedFalse();

    /**
     * Keys predating PRD-025 §3.2, picked up by the startup backfill. Revoked keys are excluded:
     * they can never authenticate again, and giving them a service user would leave a live
     * membership row that {@code revoke()} has already run past and no UI shows.
     */
    List<ApiKey> findByProjectIsNotNullAndServiceUserIsNullAndRevokedFalse();

    /** Reverse lookup from an authenticated MCP principal back to its key (PRD-025). */
    Optional<ApiKey> findByServiceUserIdAndRevokedFalse(UUID serviceUserId);
}
