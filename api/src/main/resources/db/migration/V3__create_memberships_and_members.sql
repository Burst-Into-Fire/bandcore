-- First tenant tables. Every tenant table follows the checklist in docs/DATABASE.md, and
-- SchemaGuardIT fails the build when one does not.
--
-- RLS context, set by the API at the start of every transaction (transaction-local):
--   app.band_id  the band of the request host ('' on the central host and outside requests)
--   app.user_id  the authenticated user ('' when anonymous)

-- A user's access to a band, with a role per band (ADR-0003).
CREATE TABLE memberships (
    id          uuid        PRIMARY KEY,
    band_id     uuid        NOT NULL REFERENCES bands (id),
    user_id     uuid        NOT NULL,
    role        text        NOT NULL,
    status      text        NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT memberships_band_id_key UNIQUE (band_id, id),
    CONSTRAINT memberships_band_user_key UNIQUE (band_id, user_id),
    CONSTRAINT memberships_role_valid CHECK (role IN ('ADMIN', 'MEMBER', 'READ_ONLY')),
    CONSTRAINT memberships_status_valid CHECK (status IN ('ACTIVE', 'REMOVED'))
);

-- "My bands" on the central host lists memberships by user.
CREATE INDEX memberships_user_idx ON memberships (user_id);

ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY memberships_band_isolation ON memberships
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

-- A user may read their own memberships in every band (the central "my bands" page), but
-- writes always need the band context.
CREATE POLICY memberships_own_read ON memberships FOR SELECT
    USING (user_id = NULLIF(current_setting('app.user_id', true), '')::uuid);

-- A band member (integrante): shares costs and appears in balances, with or without a login.
CREATE TABLE members (
    id            uuid        PRIMARY KEY,
    band_id       uuid        NOT NULL REFERENCES bands (id),
    display_name  text        NOT NULL,
    instrument    text,
    user_id       uuid,
    status        text        NOT NULL DEFAULT 'ACTIVE',
    joined_at     timestamptz NOT NULL,
    left_at       timestamptz,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT members_band_id_key UNIQUE (band_id, id),
    CONSTRAINT members_band_user_key UNIQUE (band_id, user_id),
    -- A member can only be linked to a user who has a membership in the same band.
    CONSTRAINT members_membership_fk FOREIGN KEY (band_id, user_id) REFERENCES memberships (band_id, user_id),
    CONSTRAINT members_display_name_length CHECK (char_length(display_name) BETWEEN 1 AND 80),
    CONSTRAINT members_instrument_length CHECK (char_length(instrument) <= 40),
    CONSTRAINT members_status_valid CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE members FORCE ROW LEVEL SECURITY;

CREATE POLICY members_band_isolation ON members
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
