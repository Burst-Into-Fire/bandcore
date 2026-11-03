-- Bands choose a default currency among the ones bandcore handles (RF40, RNF08). It pre-fills
-- money forms; every amount still stores its own currency.

ALTER TABLE bands DROP CONSTRAINT bands_currency_iso;
ALTER TABLE bands ADD CONSTRAINT bands_currency_valid CHECK (currency IN ('BRL', 'USD'));
