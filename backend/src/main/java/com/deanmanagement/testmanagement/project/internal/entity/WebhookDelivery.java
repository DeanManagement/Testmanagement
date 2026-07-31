package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single attempt-tracked delivery of a webhook payload. {@code success == null} means the
 * delivery is still pending (queued for the next attempt); {@code true}/{@code false} are terminal.
 */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class WebhookDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookEventType event;

    @Column(name = "request_body", nullable = false, columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(nullable = false)
    private int attempt;

    /** null = pending/queued, true = delivered, false = permanently failed. */
    @Column
    private Boolean success;

    @Column(columnDefinition = "TEXT")
    private String error;

    /** When the next delivery attempt is due (for pending deliveries). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
}
