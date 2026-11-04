-- Settlement payments between members (M6-02, RF18): one member pays another, in one currency, to
-- bring balances back to zero. Soft-deleted like paid expenses, so the audit trail keeps them.

CREATE TABLE settlements (
    id              uuid        PRIMARY KEY,
    band_id         uuid        NOT NULL REFERENCES bands (id),
    from_member_id  uuid        NOT NULL,
    to_member_id    uuid        NOT NULL,
    currency        text        NOT NULL,
    amount_cents    bigint      NOT NULL,
    paid_on         date        NOT NULL,
    note            text,
    created_by      uuid        NOT NULL,
    deleted_at      timestamptz,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT settlements_band_id_key UNIQUE (band_id, id),
    CONSTRAINT settlements_from_fk FOREIGN KEY (band_id, from_member_id) REFERENCES members (band_id, id),
    CONSTRAINT settlements_to_fk FOREIGN KEY (band_id, to_member_id) REFERENCES members (band_id, id),
    CONSTRAINT settlements_different_members CHECK (from_member_id <> to_member_id),
    CONSTRAINT settlements_currency_valid CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT settlements_amount_positive CHECK (amount_cents > 0 AND amount_cents <= 1000000000000),
    CONSTRAINT settlements_note_length CHECK (char_length(note) <= 500)
);

-- History by date; balances add up what each member sent and received.
CREATE INDEX settlements_band_date_idx ON settlements (band_id, paid_on DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX settlements_band_from_idx ON settlements (band_id, from_member_id);
CREATE INDEX settlements_band_to_idx ON settlements (band_id, to_member_id);

ALTER TABLE settlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE settlements FORCE ROW LEVEL SECURITY;

CREATE POLICY settlements_band_isolation ON settlements
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);
