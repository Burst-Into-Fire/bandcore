-- Audit trail (M3-03). Both tables are append-only for the application: bandcore_app may only
-- INSERT and SELECT. Retention of ip_address and user_agent: docs/security/audit-log.md.

-- What happened to a band's data, written in the same transaction as the change (audit module).
CREATE TABLE audit_events (
    id             uuid        PRIMARY KEY,
    band_id        uuid        NOT NULL REFERENCES bands (id),
    occurred_at    timestamptz NOT NULL,
    actor_user_id  uuid,
    action         text        NOT NULL,
    entity_type    text        NOT NULL,
    entity_id      uuid,
    changes        jsonb       NOT NULL DEFAULT '{}',
    request_id     text,
    ip_address     inet,
    user_agent     text,
    CONSTRAINT audit_events_band_id_key UNIQUE (band_id, id),
    CONSTRAINT audit_events_action_format CHECK (action ~ '^[a-z][a-z_]*\.[a-z][a-z_]*$'),
    CONSTRAINT audit_events_entity_type_format CHECK (entity_type ~ '^[a-z][a-z_]*$'),
    CONSTRAINT audit_events_changes_object CHECK (jsonb_typeof(changes) = 'object'),
    CONSTRAINT audit_events_request_id_length CHECK (char_length(request_id) <= 64),
    CONSTRAINT audit_events_user_agent_length CHECK (char_length(user_agent) <= 512)
);

CREATE INDEX audit_events_band_time_idx ON audit_events (band_id, occurred_at DESC);
CREATE INDEX audit_events_entity_idx ON audit_events (band_id, entity_type, entity_id);

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;

-- No UPDATE or DELETE policy: the privileges are revoked below.
CREATE POLICY audit_events_band_read ON audit_events FOR SELECT
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
CREATE POLICY audit_events_band_insert ON audit_events FOR INSERT
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM bandcore_app;

-- Account security events (identity module). Global: most happen before any band is known.
-- The host says where it happened; there is deliberately no band_id column.
CREATE TABLE security_events (
    id           uuid        PRIMARY KEY,
    occurred_at  timestamptz NOT NULL,
    event_type   text        NOT NULL,
    user_id      uuid,
    host         text,
    details      jsonb       NOT NULL DEFAULT '{}',
    request_id   text,
    ip_address   inet,
    user_agent   text,
    CONSTRAINT security_events_type_valid CHECK (event_type IN (
        'SIGNUP', 'EMAIL_VERIFIED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGIN_RATE_LIMITED',
        'REFRESH_TOKEN_REUSED', 'LOGOUT_ALL', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED')),
    CONSTRAINT security_events_details_object CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT security_events_request_id_length CHECK (char_length(request_id) <= 64),
    CONSTRAINT security_events_user_agent_length CHECK (char_length(user_agent) <= 512)
);

CREATE INDEX security_events_user_idx ON security_events (user_id, occurred_at DESC);
CREATE INDEX security_events_time_idx ON security_events (occurred_at DESC);

REVOKE UPDATE, DELETE, TRUNCATE ON security_events FROM bandcore_app;
