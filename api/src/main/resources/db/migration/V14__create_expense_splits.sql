-- Expense splits (M5-04): who owes what of each expense, as cents of the expense's currency.
-- The shares of an expense always add up to its amount (RN01): the service computes them with
-- the largest remainder method, and a deferred constraint trigger checks the sums at commit.

ALTER TABLE expenses ADD COLUMN split_mode text NOT NULL DEFAULT 'EQUAL';
ALTER TABLE expenses ALTER COLUMN split_mode DROP DEFAULT;
ALTER TABLE expenses ADD CONSTRAINT expenses_split_mode_valid
    CHECK (split_mode IN ('EQUAL', 'CUSTOM_AMOUNTS', 'CUSTOM_PERCENTAGES'));

CREATE TABLE expense_splits (
    id                  uuid    PRIMARY KEY,
    band_id             uuid    NOT NULL REFERENCES bands (id),
    expense_id          uuid    NOT NULL,
    member_id           uuid    NOT NULL,
    share_cents         bigint  NOT NULL,
    -- What was asked for, so custom splits can be edited later.
    input_cents         bigint,
    input_basis_points  bigint,
    CONSTRAINT expense_splits_member_key UNIQUE (band_id, expense_id, member_id),
    CONSTRAINT expense_splits_expense_fk FOREIGN KEY (band_id, expense_id)
        REFERENCES expenses (band_id, id) ON DELETE CASCADE,
    CONSTRAINT expense_splits_member_fk FOREIGN KEY (band_id, member_id)
        REFERENCES members (band_id, id),
    CONSTRAINT expense_splits_share_not_negative CHECK (share_cents >= 0),
    CONSTRAINT expense_splits_input_cents_not_negative CHECK (input_cents >= 0),
    CONSTRAINT expense_splits_basis_points_range CHECK (input_basis_points BETWEEN 0 AND 10000)
);

-- Balances (M6) add shares per member.
CREATE INDEX expense_splits_band_member_idx ON expense_splits (band_id, member_id);

ALTER TABLE expense_splits ENABLE ROW LEVEL SECURITY;
ALTER TABLE expense_splits FORCE ROW LEVEL SECURITY;

CREATE POLICY expense_splits_band_isolation ON expense_splits
    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
    WITH CHECK (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid);

-- Last line of defense for RN01: at commit, every expense touched by the transaction must have
-- shares adding up exactly to its amount. Runs as the caller, under row-level security.
CREATE FUNCTION check_expense_shares() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = bandcore, pg_catalog
AS $$
DECLARE
    target_band    uuid;
    target_expense uuid;
    amount         bigint;
    shared         bigint;
BEGIN
    IF TG_TABLE_NAME = 'expenses' THEN
        target_band := NEW.band_id;
        target_expense := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target_band := OLD.band_id;
        target_expense := OLD.expense_id;
    ELSE
        target_band := NEW.band_id;
        target_expense := NEW.expense_id;
    END IF;

    SELECT e.amount_cents INTO amount FROM expenses e WHERE e.band_id = target_band AND e.id = target_expense;
    IF NOT FOUND THEN
        RETURN NULL; -- the expense was deleted with its shares
    END IF;
    SELECT coalesce(sum(s.share_cents), 0) INTO shared
        FROM expense_splits s WHERE s.band_id = target_band AND s.expense_id = target_expense;
    IF shared <> amount THEN
        RAISE EXCEPTION 'expense % has shares adding up to % cents instead of %', target_expense, shared, amount
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER expense_splits_add_up
    AFTER INSERT OR UPDATE OR DELETE ON expense_splits
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_expense_shares();

CREATE CONSTRAINT TRIGGER expenses_shares_add_up
    AFTER INSERT OR UPDATE OF amount_cents ON expenses
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_expense_shares();
