package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Instance-wide authentication settings (PRD-012). A single row, created on demand.
 */
@Entity
@Table(name = "auth_settings")
@Getter
@Setter
@NoArgsConstructor
public class AuthSettings extends BaseEntity {

    /**
     * When false, password login is refused for everyone except system admins. Admins keep it as a
     * break-glass: a provider misconfigured after SSO becomes mandatory would otherwise lock the
     * instance out with no route back in short of direct database access.
     */
    @Column(name = "local_login_enabled", nullable = false)
    private boolean localLoginEnabled = true;
}
