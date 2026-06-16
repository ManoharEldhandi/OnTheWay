-- ===================================================================
-- V5 — Order prep-start time (drives the auto-advance scheduler)
-- ===================================================================

ALTER TABLE orders ADD COLUMN prep_start_at DATETIME;
