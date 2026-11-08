ALTER TABLE rating_ledger ADD COLUMN stage_order INTEGER;
ALTER TABLE rating_ledger ADD COLUMN round_number INTEGER;
ALTER TABLE rating_ledger ADD COLUMN match_number INTEGER;
ALTER TABLE rating_settlements ADD COLUMN finalized_at TIMESTAMPTZ;
