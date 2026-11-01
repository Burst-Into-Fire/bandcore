-- Global accounts (ADR-0003): one email, one account, any number of bands. Only the identity
-- module reads or writes these tables (ArchitectureRulesTest).

CREATE TABLE users (
    id                 uuid        PRIMARY KEY,
    email              text        NOT NULL,
    password_hash      text        NOT NULL,
    display_name       text        NOT NULL,
    email_verified_at  timestamptz,
    status             text        NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT users_email_length CHECK (char_length(email) BETWEEN 3 AND 254),
    CONSTRAINT users_display_name_length CHECK (char_length(display_name) BETWEEN 1 AND 80),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX users_email_key ON users (email);

-- Single-use verification links; only the SHA-256 hash of the token is stored.
CREATE TABLE email_verification_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT email_verification_tokens_hash_key UNIQUE (token_hash)
);

CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id);

-- Memberships were created before users existed (V3).
ALTER TABLE memberships ADD CONSTRAINT memberships_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
