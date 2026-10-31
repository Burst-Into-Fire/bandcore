-- Who created each band, to limit how many bands one account can create.
ALTER TABLE bands ADD COLUMN created_by uuid REFERENCES users (id);

CREATE INDEX bands_created_by_idx ON bands (created_by);
