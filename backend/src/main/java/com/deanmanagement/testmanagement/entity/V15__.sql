CREATE TABLE api_keys
(
    id           UUID         NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    name         VARCHAR(255) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL,
    key_prefix   VARCHAR(8)   NOT NULL,
    revoked      BOOLEAN      NOT NULL,
    last_used_at TIMESTAMP,
    CONSTRAINT pk_api_keys PRIMARY KEY (id)
);