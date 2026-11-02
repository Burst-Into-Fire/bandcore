-- Invitations to a band (M4-02). The invitation is band data (RLS); the token lookup is global,
-- because an invitation is accepted on the central host before any band is known.

CREATE TABLE invitations (
    id           uuid        PRIMARY KEY,
    band_id      uuid        NOT NULL REFERENCES bands (id),
    email        text        NOT NULL,
    role         text        NOT NULL,
    member_id    uuid,
    status       text        NOT NULL DEFAULT 'PENDING',
    expires_at   timestamptz NOT NULL,
    invited_by   uuid        NOT NULL,
    accepted_by  uuid,
    accepted_at  timestamptz,
    revoked_at   timestamptz,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT invitations_band_id_key UNIQUE (band_id, id),
    CONSTRAINT invitations_member_fk FOREIGN KEY (band_id, member_id) REFERENCES members (band_id, id),
    CONSTRAINT invitations_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT invitations_role_valid CHECK (role IN ('ADMIN', 'MEMBER', 'READ_ONLY')),
    CONSTRAINT invitations_status_valid CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);

-- One pending invitation per email and band: send again instead of inviting twice.
CREATE UNIQUE INDEX invitations_pending_email_key ON invitations (band_id, email) WHERE status = 'PENDING';
CREATE INDEX invitations_band_created_idx ON invitations (band_id, created_at DESC);

ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations FORCE ROW LEVEL SECURITY;

CREATE POLICY invitations_band_isolation ON invitations
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

-- Global lookup from the SHA-256 hash of an invitation token to its band. Holds no band data:
-- the email and role stay in invitations, behind RLS. One row per pending invitation; the row
-- is deleted when the invitation is accepted, revoked or sent again.
CREATE TABLE invitation_tokens (
    invitation_id  uuid        PRIMARY KEY,
    band_id        uuid        NOT NULL REFERENCES bands (id),
    token_hash     bytea       NOT NULL,
    expires_at     timestamptz NOT NULL,
    CONSTRAINT invitation_tokens_hash_key UNIQUE (token_hash),
    CONSTRAINT invitation_tokens_invitation_fk FOREIGN KEY (band_id, invitation_id)
        REFERENCES invitations (band_id, id) ON DELETE CASCADE
);
