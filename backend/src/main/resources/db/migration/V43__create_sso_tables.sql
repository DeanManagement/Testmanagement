CREATE TABLE sso_providers (
    id UUID PRIMARY KEY,
    slug VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    issuer_uri VARCHAR(500) NOT NULL,
    client_id VARCHAR(300) NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    scopes VARCHAR(300) NOT NULL DEFAULT 'openid,profile,email',
    email_claim VARCHAR(100) NOT NULL DEFAULT 'email',
    name_claim VARCHAR(100) NOT NULL DEFAULT 'name',
    admin_claim VARCHAR(100),
    admin_claim_value VARCHAR(200),
    -- Off by default: linking an SSO identity to an existing account on a matching email is only
    -- safe if the IdP controls what email a user may assert.
    trust_email_for_linking BOOLEAN NOT NULL DEFAULT FALSE,
    auto_provision BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_error TEXT,
    last_error_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_sso_providers_slug UNIQUE (slug)
);

CREATE TABLE sso_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider_id UUID NOT NULL,
    subject VARCHAR(300) NOT NULL,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_sso_identities_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sso_identities_provider FOREIGN KEY (provider_id) REFERENCES sso_providers(id) ON DELETE CASCADE,
    -- The durable identity key is (provider, subject), never the email: emails change hands and
    -- some IdPs let a user assert an arbitrary one.
    CONSTRAINT uq_sso_identities_provider_subject UNIQUE (provider_id, subject),
    -- One identity per user per provider, so a second subject cannot quietly attach to the account.
    CONSTRAINT uq_sso_identities_provider_user UNIQUE (provider_id, user_id)
);

CREATE INDEX idx_sso_identities_user ON sso_identities (user_id);

CREATE TABLE auth_settings (
    id UUID PRIMARY KEY,
    -- When false, only system admins may still authenticate with a password. That break-glass is
    -- deliberate: a misconfigured IdP would otherwise lock every account out irrecoverably.
    local_login_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID
);
