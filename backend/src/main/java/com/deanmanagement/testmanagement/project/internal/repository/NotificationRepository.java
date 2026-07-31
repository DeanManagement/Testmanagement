package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByUserIdAndEntityIdAndActionAndCreatedAtGreaterThan(
            UUID userId, UUID entityId, AuditAction action, Instant cutoff);

    @Query("SELECT n.userId FROM Notification n WHERE n.userId IN :userIds " +
           "AND n.entityId = :entityId AND n.action = :action AND n.createdAt > :cutoff")
    Set<UUID> findRecentlyNotifiedUserIds(@Param("userIds") Collection<UUID> userIds,
                                          @Param("entityId") UUID entityId,
                                          @Param("action") AuditAction action,
                                          @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
