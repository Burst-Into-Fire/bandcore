-- Refresh tokens (ADR-0004). Global table: a token is looked up by its hash before any band is
-- known, and logout-all revokes a user's sessions in every band. Only the identity module uses
-- it. Only the SHA-256 hash of each token is stored.

CREATE TABLE refresh_tokens (
    id                 uuid        PRIMARY KEY,
    family_id          uuid        NOT NULL,
    user_id            uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    band_id            uuid        REFERENCES bands (id),
    audience           text        NOT NULL,
    token_hash         bytea       NOT NULL,
    created_at         timestamptz NOT NULL,
    expires_at         timestamptz NOT NULL,
    family_expires_at  timestamptz NOT NULL,
    rotated_at         timestamptz,
    revoked_at         timestamptz,
    CONSTRAINT refresh_tokens_hash_key UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_expiry_valid CHECK (expires_at <= family_expires_at)
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id, band_id);
