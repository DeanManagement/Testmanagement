CREATE TABLE users (
    id UUID PRIMARY KEY,
    oidc_subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    system_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_oidc_subject ON users(oidc_subject);
