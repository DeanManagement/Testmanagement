package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The link between a local user and their identity at one provider (PRD-012 §4.1).
 *
 * <p>{@code (providerId, subject)} is the durable key. Email is deliberately not part of it:
 * addresses get reassigned between employees, and some IdPs let a user assert an arbitrary one, so
 * keying on email would make account takeover a configuration mistake away.
 */
@Entity
@Table(name = "sso_identities")
@Getter
@Setter
@NoArgsConstructor
public class SsoIdentity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    /** The IdP's {@code sub} claim — opaque, stable, and unique within the provider. */
    @Column(nullable = false, length = 300)
    private String subject;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
