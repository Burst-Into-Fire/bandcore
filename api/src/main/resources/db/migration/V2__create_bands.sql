-- Bands are the tenants. Global table: it has no band_id and no RLS, because it is the tenant
-- itself. Only the tenancy module reads or writes it (ArchitectureRulesTest).

CREATE TABLE bands (
    id          uuid        PRIMARY KEY,
    slug        text        NOT NULL,
    name        text        NOT NULL,
    currency    text        NOT NULL DEFAULT 'BRL',
    timezone    text        NOT NULL DEFAULT 'America/Sao_Paulo',
    status      text        NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT bands_slug_length CHECK (char_length(slug) BETWEEN 3 AND 32),
    CONSTRAINT bands_name_length CHECK (char_length(name) BETWEEN 1 AND 80),
    CONSTRAINT bands_currency_iso CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT bands_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- The slug is the subdomain: unique regardless of case.
CREATE UNIQUE INDEX bands_slug_lower_key ON bands (lower(slug));
