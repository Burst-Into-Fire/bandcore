-- Password reset (M3-01). Same shape as email_verification_tokens: only the SHA-256 hash of the
-- token is stored, and a token is single use.

CREATE TABLE password_reset_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT password_reset_tokens_hash_key UNIQUE (token_hash)
);

CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);

-- Access tokens issued before this instant are refused, so a password reset or "log out
-- everywhere" ends every session at once instead of after the access token expires.
ALTER TABLE users ADD COLUMN sessions_valid_after timestamptz;
