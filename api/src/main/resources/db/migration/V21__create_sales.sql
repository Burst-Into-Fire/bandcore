-- Online sales are the idempotency record and the immutable price/cost snapshot for each item.
CREATE TABLE sales (
    id              uuid        PRIMARY KEY,
    band_id         uuid        NOT NULL REFERENCES bands (id),
    event_id        uuid,
    seller_user_id  uuid        NOT NULL REFERENCES users (id),
    sold_at         timestamptz NOT NULL,
    payment_method  text        NOT NULL,
    currency        text        NOT NULL,
    total_cents     bigint      NOT NULL,
    status          text        NOT NULL DEFAULT 'COMPLETED',
    source          text        NOT NULL DEFAULT 'ONLINE',
    request_hash    text        NOT NULL,
    created_at      timestamptz NOT NULL,
    CONSTRAINT sales_band_id_key UNIQUE (band_id, id),
    CONSTRAINT sales_event_fk FOREIGN KEY (band_id, event_id) REFERENCES events (band_id, id),
    CONSTRAINT sales_payment_method_valid CHECK (payment_method IN ('CASH', 'PIX', 'CARD', 'OTHER')),
    CONSTRAINT sales_currency_valid CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT sales_total_valid CHECK (total_cents >= 0 AND total_cents <= 1000000000000),
    CONSTRAINT sales_status_valid CHECK (status IN ('COMPLETED', 'PENDING_RECONCILIATION', 'CANCELLED')),
    CONSTRAINT sales_source_valid CHECK (source IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT sales_request_hash_valid CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX sales_band_sold_at_idx ON sales (band_id, sold_at DESC, id);

CREATE TABLE sale_items (
    id                  uuid        PRIMARY KEY,
    band_id             uuid        NOT NULL REFERENCES bands (id),
    sale_id             uuid        NOT NULL,
    variant_id          uuid        NOT NULL,
    position            integer     NOT NULL,
    quantity            integer     NOT NULL,
    unit_price_cents    bigint      NOT NULL,
    unit_cost_cents     bigint,
    discount_cents      bigint      NOT NULL DEFAULT 0,
    discount_reason     text,
    CONSTRAINT sale_items_band_id_key UNIQUE (band_id, id),
    CONSTRAINT sale_items_sale_fk FOREIGN KEY (band_id, sale_id)
        REFERENCES sales (band_id, id) ON DELETE CASCADE,
    CONSTRAINT sale_items_variant_fk FOREIGN KEY (band_id, variant_id)
        REFERENCES product_variants (band_id, id),
    CONSTRAINT sale_items_one_variant_per_sale UNIQUE (band_id, sale_id, variant_id),
    CONSTRAINT sale_items_position_valid CHECK (position >= 0),
    CONSTRAINT sale_items_quantity_valid CHECK (quantity BETWEEN 1 AND 1000000),
    CONSTRAINT sale_items_unit_price_valid CHECK (unit_price_cents >= 0 AND unit_price_cents <= 1000000000),
    CONSTRAINT sale_items_unit_cost_valid CHECK (unit_cost_cents >= 0),
    CONSTRAINT sale_items_discount_valid CHECK (discount_cents >= 0 AND discount_cents <= 1000000000000),
    CONSTRAINT sale_items_discount_reason_length CHECK (char_length(discount_reason) BETWEEN 1 AND 200),
    CONSTRAINT sale_items_discount_reason_required CHECK ((discount_cents > 0) = (discount_reason IS NOT NULL))
);

CREATE INDEX sale_items_band_sale_idx ON sale_items (band_id, sale_id, position);
CREATE INDEX sale_items_band_variant_idx ON sale_items (band_id, variant_id);

ALTER TABLE sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales FORCE ROW LEVEL SECURITY;

CREATE POLICY sales_band_isolation ON sales
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

ALTER TABLE sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_items FORCE ROW LEVEL SECURITY;

CREATE POLICY sale_items_band_isolation ON sale_items
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
