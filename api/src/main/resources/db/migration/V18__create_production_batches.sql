-- Production batches (M7-03, RF24, ADR-0009): merch received from a supplier at a cost. Each line
-- brings units of a variant into stock (a BATCH_IN movement) and updates the variant's weighted
-- average unit cost. Quantities and costs are immutable; a batch can be cancelled while it is
-- still the latest movement of each of its variants, which restores the stored "before" values.

-- Null until the variant's first batch. Kept when stock reaches zero; the next batch replaces it.
ALTER TABLE stock_levels ADD COLUMN average_unit_cost_cents bigint;
ALTER TABLE stock_levels ADD CONSTRAINT stock_levels_average_cost_valid CHECK (average_unit_cost_cents >= 0);

CREATE TABLE production_batches (
    id                uuid        PRIMARY KEY,
    band_id           uuid        NOT NULL REFERENCES bands (id),
    supplier          text,
    ordered_on        date,
    received_on       date        NOT NULL,
    currency          text        NOT NULL,
    cost_mode         text        NOT NULL,
    total_cost_cents  bigint      NOT NULL,
    notes             text,
    status            text        NOT NULL DEFAULT 'RECEIVED',
    cancelled_at      timestamptz,
    cancelled_by      uuid,
    created_by        uuid        NOT NULL,
    version           bigint      NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT production_batches_band_id_key UNIQUE (band_id, id),
    CONSTRAINT production_batches_supplier_length CHECK (char_length(supplier) BETWEEN 1 AND 120),
    CONSTRAINT production_batches_notes_length CHECK (char_length(notes) <= 500),
    CONSTRAINT production_batches_currency_valid CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT production_batches_cost_mode_valid CHECK (cost_mode IN ('PER_ITEM', 'ALLOCATED')),
    CONSTRAINT production_batches_total_valid CHECK (total_cost_cents >= 0 AND total_cost_cents <= 1000000000000),
    CONSTRAINT production_batches_status_valid CHECK (status IN ('RECEIVED', 'CANCELLED')),
    CONSTRAINT production_batches_cancellation_consistent CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX production_batches_band_received_idx ON production_batches (band_id, received_on DESC, id DESC);

CREATE TABLE batch_items (
    id                         uuid    PRIMARY KEY,
    band_id                    uuid    NOT NULL REFERENCES bands (id),
    batch_id                   uuid    NOT NULL,
    variant_id                 uuid    NOT NULL,
    position                   integer NOT NULL,
    quantity                   integer NOT NULL,
    -- What this line cost: quantity × unit cost, or its share of the batch total.
    line_cost_cents            bigint  NOT NULL,
    -- The variant's stock right before the batch, to cancel it exactly.
    quantity_before            integer NOT NULL,
    average_cost_before_cents  bigint,
    movement_id                uuid    NOT NULL,
    CONSTRAINT batch_items_band_id_key UNIQUE (band_id, id),
    CONSTRAINT batch_items_batch_fk FOREIGN KEY (band_id, batch_id)
        REFERENCES production_batches (band_id, id) ON DELETE CASCADE,
    CONSTRAINT batch_items_variant_fk FOREIGN KEY (band_id, variant_id) REFERENCES product_variants (band_id, id),
    CONSTRAINT batch_items_movement_fk FOREIGN KEY (band_id, movement_id) REFERENCES stock_movements (band_id, id),
    CONSTRAINT batch_items_one_line_per_variant UNIQUE (band_id, batch_id, variant_id),
    CONSTRAINT batch_items_quantity_valid CHECK (quantity BETWEEN 1 AND 100000),
    CONSTRAINT batch_items_line_cost_valid CHECK (line_cost_cents >= 0),
    CONSTRAINT batch_items_before_valid CHECK (quantity_before >= 0 AND average_cost_before_cents >= 0)
);

CREATE INDEX batch_items_band_variant_idx ON batch_items (band_id, variant_id);

ALTER TABLE production_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE production_batches FORCE ROW LEVEL SECURITY;

CREATE POLICY production_batches_band_isolation ON production_batches
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

ALTER TABLE batch_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE batch_items FORCE ROW LEVEL SECURITY;

CREATE POLICY batch_items_band_isolation ON batch_items
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
