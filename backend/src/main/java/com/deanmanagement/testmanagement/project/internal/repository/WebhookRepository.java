package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("SELECT DISTINCT w FROM Webhook w JOIN w.events e " +
            "WHERE w.project.id = :projectId AND w.active = true AND e = :event")
    List<Webhook> findActiveForEvent(@Param("projectId") UUID projectId,
                                     @Param("event") WebhookEventType event);
}
