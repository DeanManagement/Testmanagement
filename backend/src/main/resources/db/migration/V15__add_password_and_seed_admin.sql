-- Add password_hash column
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- Drop oidc_subject column and its index
DROP INDEX IF EXISTS idx_users_oidc_subject;
ALTER TABLE users DROP COLUMN oidc_subject;

-- Add unique constraint on email
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);

-- Seed default admin user (password: admin)
INSERT INTO users (id, email, display_name, system_admin, password_hash, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@localhost',
    'Administrator',
    TRUE,
    '$2a$10$gUXGMlOevPlDaqOKXiPiqucJhS.ynFYWOYFgfTYoBWF4F9lkVluPW',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
