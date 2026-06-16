-- ===================================================================
-- V4 — Payment gateway metadata
-- Records which provider processed a payment and its external reference.
-- ===================================================================

ALTER TABLE payments ADD COLUMN gateway VARCHAR(20);
ALTER TABLE payments ADD COLUMN gateway_reference VARCHAR(100);
