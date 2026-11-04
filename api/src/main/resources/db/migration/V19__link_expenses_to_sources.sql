-- Batch cost as a split expense (M7-04, RF34): an expense can come from another record, today a
-- production batch. One expense per source at most, even if that expense is later deleted.

ALTER TABLE expenses ADD COLUMN source_type text;
ALTER TABLE expenses ADD COLUMN source_id uuid;
ALTER TABLE expenses ADD CONSTRAINT expenses_source_type_valid CHECK (source_type IN ('BATCH'));
ALTER TABLE expenses ADD CONSTRAINT expenses_source_complete CHECK ((source_type IS NULL) = (source_id IS NULL));

CREATE UNIQUE INDEX expenses_source_key ON expenses (band_id, source_type, source_id) WHERE source_type IS NOT NULL;

-- Whether registering the batch also created an expense, so cancelling it can warn that the
-- expense does not change.
ALTER TABLE production_batches ADD COLUMN expense_requested boolean NOT NULL DEFAULT false;
