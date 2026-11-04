-- Stock ledger (M7-02, RN03): the quantity on hand of each variant, which can never go below
-- zero, and an append-only list of every movement that explains it. Changes go through the
-- merch module's StockService, which updates the level and inserts the movement in one
-- statement.

CREATE TABLE stock_levels (
    band_id           uuid        NOT NULL REFERENCES bands (id),
    variant_id        uuid        NOT NULL,
    quantity_on_hand  integer     NOT NULL DEFAULT 0,
    -- The latest movement of the variant (no foreign key: written in the same statement as the
    -- movement). A production batch can be cancelled only while it is still the latest.
    last_movement_id  uuid,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT stock_levels_pkey PRIMARY KEY (band_id, variant_id),
    CONSTRAINT stock_levels_variant_fk FOREIGN KEY (band_id, variant_id)
        REFERENCES product_variants (band_id, id) ON DELETE CASCADE,
    CONSTRAINT stock_levels_quantity_not_negative CHECK (quantity_on_hand >= 0)
);

-- Variants created before this migration start with nothing on hand.
INSERT INTO stock_levels (band_id, variant_id, updated_at)
SELECT band_id, id, now() FROM product_variants;

CREATE TABLE stock_movements (
    id              uuid        PRIMARY KEY,
    band_id         uuid        NOT NULL REFERENCES bands (id),
    -- Order of the movements of a variant: they are written under its stock level's row lock.
    sequence        bigint      GENERATED ALWAYS AS IDENTITY,
    variant_id      uuid        NOT NULL,
    type            text        NOT NULL,
    quantity_delta  integer     NOT NULL,
    quantity_after  integer     NOT NULL,
    source_type     text,
    source_id       uuid,
    reason          text,
    note            text,
    actor_user_id   uuid,
    occurred_at     timestamptz NOT NULL,
    CONSTRAINT stock_movements_band_id_key UNIQUE (band_id, id),
    -- No cascade: a variant with movements cannot be deleted, only archived.
    CONSTRAINT stock_movements_variant_fk FOREIGN KEY (band_id, variant_id) REFERENCES product_variants (band_id, id),
    CONSTRAINT stock_movements_type_valid CHECK (type IN
        ('BATCH_IN', 'BATCH_REVERSAL', 'SALE', 'REFUND', 'ADJUSTMENT', 'RECONCILIATION')),
    CONSTRAINT stock_movements_delta_not_zero CHECK (quantity_delta <> 0),
    CONSTRAINT stock_movements_after_not_negative CHECK (quantity_after >= 0),
    CONSTRAINT stock_movements_source_valid CHECK (source_type IN ('BATCH', 'SALE', 'REFUND')),
    CONSTRAINT stock_movements_source_complete CHECK ((source_type IS NULL) = (source_id IS NULL)),
    CONSTRAINT stock_movements_reason_valid CHECK (reason IN ('COUNT', 'DAMAGED', 'LOST', 'GIFT', 'OTHER')),
    CONSTRAINT stock_movements_reason_for_adjustments CHECK ((type = 'ADJUSTMENT') = (reason IS NOT NULL)),
    CONSTRAINT stock_movements_note_length CHECK (char_length(note) <= 500)
);

-- History of a variant, newest first.
CREATE INDEX stock_movements_variant_idx ON stock_movements (band_id, variant_id, sequence DESC);

ALTER TABLE stock_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_levels FORCE ROW LEVEL SECURITY;

CREATE POLICY stock_levels_band_isolation ON stock_levels
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

-- Append-only: the application reads and inserts movements, never changes or deletes them.
ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements FORCE ROW LEVEL SECURITY;

CREATE POLICY stock_movements_band_select ON stock_movements FOR SELECT
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
CREATE POLICY stock_movements_band_insert ON stock_movements FOR INSERT
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

REVOKE UPDATE, DELETE, TRUNCATE ON stock_movements FROM bandcore_app;
