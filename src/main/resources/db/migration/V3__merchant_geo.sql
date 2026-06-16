-- ===================================================================
-- V3 — Store geo + preparation time (enables the ETA engine & discovery)
-- Columns are nullable so existing merchant rows remain valid.
-- ===================================================================

ALTER TABLE merchants ADD COLUMN latitude DOUBLE;
ALTER TABLE merchants ADD COLUMN longitude DOUBLE;
ALTER TABLE merchants ADD COLUMN prep_time_mins INT;

CREATE INDEX idx_merchants_geo ON merchants (latitude, longitude);
