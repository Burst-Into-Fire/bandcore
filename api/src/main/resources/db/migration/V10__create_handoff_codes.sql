-- Moving a session to another host without typing the password again (M4-05). Sessions are
-- host-only, so the current host issues a one-time code bound to the user, the target band (null
-- for the central host) and the target host, and the target host exchanges it for a session.
-- Global table owned by identity; only the SHA-256 hash of each code is stored.

CREATE TABLE handoff_codes (
    id           uuid        PRIMARY KEY,
    code_hash    bytea       NOT NULL,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_band  uuid        REFERENCES bands (id),
    target_host  text        NOT NULL,
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    created_at   timestamptz NOT NULL,
    CONSTRAINT handoff_codes_hash_key UNIQUE (code_hash)
);

CREATE INDEX handoff_codes_expires_idx ON handoff_codes (expires_at);
