CREATE TABLE api_keys (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    key_hash   VARCHAR(64)  NOT NULL,
    key_prefix VARCHAR(8)   NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
