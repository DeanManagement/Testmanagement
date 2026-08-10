package com.deanmanagement.testmanagement.user;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String displayName;

    private String passwordHash;

    @Column(nullable = false)
    private boolean systemAdmin;

    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange;

    /**
     * PRD-025 §3.2: a non-human account backing one API key. Service accounts hold a real
     * {@code ProjectMember} role so API-key requests are authorized like any other caller, but they
     * can never sign in — by password or by SSO email linking — and are hidden from user
     * administration, member lists and assignee pickers.
     */
    @Column(name = "service_account", nullable = false)
    private boolean serviceAccount;

    /**
     * PRD-020: embedded as a JWT claim and checked on every request; incrementing it
     * invalidates all outstanding tokens (server-side logout, password change).
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
}
