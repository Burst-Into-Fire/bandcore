-- Expense categories (M5-02). New bands get the defaults from the finance module when they are
-- created (BandCreated); bands that already exist get them here.

CREATE TABLE expense_categories (
    id           uuid        PRIMARY KEY,
    band_id      uuid        NOT NULL REFERENCES bands (id),
    name         text        NOT NULL,
    default_key  text,
    color        text,
    archived_at  timestamptz,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT expense_categories_band_id_key UNIQUE (band_id, id),
    CONSTRAINT expense_categories_name_length CHECK (char_length(name) BETWEEN 1 AND 40),
    CONSTRAINT expense_categories_default_key_valid CHECK (default_key IN
        ('REHEARSAL_SPACE', 'GEAR', 'VIDEO', 'PHOTOGRAPHY', 'TRAVEL', 'MERCH', 'OTHER')),
    CONSTRAINT expense_categories_color_hex CHECK (color ~ '^#[0-9a-f]{6}$')
);

-- Unique per band regardless of case, archived categories included.
CREATE UNIQUE INDEX expense_categories_name_key ON expense_categories (band_id, lower(name));
CREATE UNIQUE INDEX expense_categories_default_key ON expense_categories (band_id, default_key)
    WHERE default_key IS NOT NULL;

-- Defaults for existing bands, written before row-level security is turned on. The ids are
-- UUIDv7 like the application's (ADR-0007): the millisecond timestamp over a random UUID, with
-- the version bits set to 7.
INSERT INTO expense_categories (id, band_id, name, default_key, created_at, updated_at)
SELECT encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid())
           PLACING substring(int8send((extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3)
           FROM 1 FOR 6), 52, 1), 53, 1), 'hex')::uuid,
       b.id, d.name, d.default_key, now(), now()
FROM bands b
CROSS JOIN (VALUES
    ('REHEARSAL_SPACE', 'Estúdio de ensaio'),
    ('GEAR', 'Equipamento'),
    ('VIDEO', 'Vídeo'),
    ('PHOTOGRAPHY', 'Fotografia'),
    ('TRAVEL', 'Viagem'),
    ('MERCH', 'Merch'),
    ('OTHER', 'Outros')
) AS d (default_key, name);

ALTER TABLE expense_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE expense_categories FORCE ROW LEVEL SECURITY;

CREATE POLICY expense_categories_band_isolation ON expense_categories
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
