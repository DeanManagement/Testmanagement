package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One configured OpenID Connect provider (PRD-012). Rows are managed by system admins at runtime;
 * there is no restart and no property file involved.
 *
 * <p>The client secret is stored encrypted and never leaves the server.
 */
@Entity
@Table(name = "sso_providers")
@Getter
@Setter
@NoArgsConstructor
public class SsoProvider extends BaseEntity {

    /** Appears in the login and callback URLs, so it is stable and URL-safe. */
    @Column(nullable = false, length = 50, unique = true)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** Discovery root, e.g. {@code https://keycloak.example.com/realms/acme}. */
    @Column(name = "issuer_uri", nullable = false, length = 500)
    private String issuerUri;

    @Column(name = "client_id", nullable = false, length = 300)
    private String clientId;

    @Column(name = "client_secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    /** Comma-separated; {@code openid} is always required and enforced on save. */
    @Column(nullable = false, length = 300)
    private String scopes = "openid,profile,email";

    @Column(name = "email_claim", nullable = false, length = 100)
    private String emailClaim = "email";

    @Column(name = "name_claim", nullable = false, length = 100)
    private String nameClaim = "name";

    /** Optional claim that grants the system-admin flag when it holds {@link #adminClaimValue}. */
    @Column(name = "admin_claim", length = 100)
    private String adminClaim;

    @Column(name = "admin_claim_value", length = 200)
    private String adminClaimValue;

    /**
     * Whether a verified email from this provider may be used to adopt an existing local account.
     * Off by default — see {@code SsoLoginService} for why this is the account-takeover boundary.
     */
    @Column(name = "trust_email_for_linking", nullable = false)
    private boolean trustEmailForLinking = false;

    /** Whether an unknown user is created on first login, with no project access. */
    @Column(name = "auto_provision", nullable = false)
    private boolean autoProvision = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;
}
