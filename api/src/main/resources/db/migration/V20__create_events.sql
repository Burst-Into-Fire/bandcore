-- Shows are tenant data. Sales will reference an open event in M8-02.
CREATE TABLE events (
    id          uuid        PRIMARY KEY,
    band_id     uuid        NOT NULL REFERENCES bands (id),
    name        text        NOT NULL,
    venue       text,
    city        text,
    starts_at   timestamptz NOT NULL,
    status      text        NOT NULL DEFAULT 'OPEN',
    closed_at   timestamptz,
    closed_by   uuid        REFERENCES users (id),
    created_by  uuid        NOT NULL,
    version     bigint      NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT events_band_id_key UNIQUE (band_id, id),
    CONSTRAINT events_name_length CHECK (char_length(name) BETWEEN 1 AND 120),
    CONSTRAINT events_venue_length CHECK (char_length(venue) BETWEEN 1 AND 120),
    CONSTRAINT events_city_length CHECK (char_length(city) BETWEEN 1 AND 80),
    CONSTRAINT events_status_valid CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT events_closure_consistent CHECK (
        (status = 'OPEN' AND closed_at IS NULL AND closed_by IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL)
    )
);

CREATE INDEX events_band_starts_at_idx ON events (band_id, starts_at, id);

ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE events FORCE ROW LEVEL SECURITY;

CREATE POLICY events_band_isolation ON events
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
