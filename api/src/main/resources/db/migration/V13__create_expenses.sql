-- Expenses (M5-03): planned or paid, in BRL or USD, as integer cents (ADR-0006). Only paid,
-- non-deleted expenses will count in balances (M6).

CREATE TABLE expenses (
    id                 uuid        PRIMARY KEY,
    band_id            uuid        NOT NULL REFERENCES bands (id),
    description        text        NOT NULL,
    category_id        uuid        NOT NULL,
    currency           text        NOT NULL,
    amount_cents       bigint      NOT NULL,
    expense_date       date        NOT NULL,
    status             text        NOT NULL,
    paid_by_member_id  uuid,
    paid_on            date,
    notes              text,
    created_by         uuid        NOT NULL,
    deleted_at         timestamptz,
    version            bigint      NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT expenses_band_id_key UNIQUE (band_id, id),
    CONSTRAINT expenses_category_fk FOREIGN KEY (band_id, category_id)
        REFERENCES expense_categories (band_id, id),
    CONSTRAINT expenses_payer_fk FOREIGN KEY (band_id, paid_by_member_id)
        REFERENCES members (band_id, id),
    CONSTRAINT expenses_description_length CHECK (char_length(description) BETWEEN 1 AND 120),
    CONSTRAINT expenses_notes_length CHECK (char_length(notes) <= 500),
    CONSTRAINT expenses_currency_valid CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT expenses_amount_positive CHECK (amount_cents > 0 AND amount_cents <= 1000000000000),
    CONSTRAINT expenses_status_valid CHECK (status IN ('PLANNED', 'PAID')),
    -- A paid expense says who paid and when; a planned one has neither yet.
    CONSTRAINT expenses_payment_consistent CHECK (
        (status = 'PAID' AND paid_by_member_id IS NOT NULL AND paid_on IS NOT NULL)
        OR (status = 'PLANNED' AND paid_by_member_id IS NULL AND paid_on IS NULL))
);

-- Lists by month and status; balances and filters by payer and category.
CREATE INDEX expenses_band_date_idx ON expenses (band_id, expense_date DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX expenses_band_status_date_idx ON expenses (band_id, status, expense_date) WHERE deleted_at IS NULL;
CREATE INDEX expenses_band_payer_idx ON expenses (band_id, paid_by_member_id);
CREATE INDEX expenses_band_category_idx ON expenses (band_id, category_id);

ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE expenses FORCE ROW LEVEL SECURITY;

CREATE POLICY expenses_band_isolation ON expenses
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
