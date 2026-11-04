-- Merch catalog (M7-01): products and their variants. A product has one currency for its prices
-- and its cost (ADR-0009); stock and price live on the variant. A product without variations has
-- one default variant, with neither size nor color.

CREATE TABLE products (
    id                   uuid        PRIMARY KEY,
    band_id              uuid        NOT NULL REFERENCES bands (id),
    name                 text        NOT NULL,
    description          text,
    currency             text        NOT NULL,
    default_price_cents  bigint      NOT NULL,
    archived_at          timestamptz,
    created_by           uuid        NOT NULL,
    version              bigint      NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    CONSTRAINT products_band_id_key UNIQUE (band_id, id),
    CONSTRAINT products_name_length CHECK (char_length(name) BETWEEN 1 AND 120),
    CONSTRAINT products_description_length CHECK (char_length(description) <= 500),
    CONSTRAINT products_currency_valid CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT products_price_valid CHECK (default_price_cents >= 0 AND default_price_cents <= 1000000000)
);

-- Unique per band regardless of case, archived products included.
CREATE UNIQUE INDEX products_name_key ON products (band_id, lower(name));

CREATE TABLE product_variants (
    id                   uuid        PRIMARY KEY,
    band_id              uuid        NOT NULL REFERENCES bands (id),
    product_id           uuid        NOT NULL,
    size                 text,
    color                text,
    sku                  text,
    price_cents          bigint,
    low_stock_threshold  integer     NOT NULL DEFAULT 0,
    position             integer     NOT NULL,
    archived_at          timestamptz,
    version              bigint      NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    CONSTRAINT product_variants_band_id_key UNIQUE (band_id, id),
    CONSTRAINT product_variants_product_fk FOREIGN KEY (band_id, product_id)
        REFERENCES products (band_id, id) ON DELETE CASCADE,
    CONSTRAINT product_variants_size_length CHECK (char_length(size) BETWEEN 1 AND 20),
    CONSTRAINT product_variants_color_length CHECK (char_length(color) BETWEEN 1 AND 40),
    CONSTRAINT product_variants_sku_length CHECK (char_length(sku) BETWEEN 1 AND 40),
    CONSTRAINT product_variants_price_valid CHECK (price_cents >= 0 AND price_cents <= 1000000000),
    CONSTRAINT product_variants_threshold_valid CHECK (low_stock_threshold BETWEEN 0 AND 100000),
    CONSTRAINT product_variants_position_valid CHECK (position >= 0)
);

-- One variant per size and color of a product (the default variant has neither).
CREATE UNIQUE INDEX product_variants_attributes_key ON product_variants
    (band_id, product_id, lower(coalesce(size, '')), lower(coalesce(color, '')));
-- SKUs are optional and unique per band when set, regardless of case.
CREATE UNIQUE INDEX product_variants_sku_key ON product_variants (band_id, lower(sku)) WHERE sku IS NOT NULL;

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;

CREATE POLICY products_band_isolation ON products
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

ALTER TABLE product_variants ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_variants FORCE ROW LEVEL SECURITY;

CREATE POLICY product_variants_band_isolation ON product_variants
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
