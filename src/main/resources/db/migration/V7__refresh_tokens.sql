-- V7 — Durable refresh tokens (rotation + logout)
CREATE TABLE refresh_tokens (
    refresh_token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    token_hash       VARCHAR(128) NOT NULL UNIQUE,
    expires_at       DATETIME     NOT NULL,
    revoked_at       DATETIME,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
