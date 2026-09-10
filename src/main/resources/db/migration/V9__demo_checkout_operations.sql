-- Make payment retries and hand-off at pickup explicit.  Existing payment rows remain valid
-- (one successful payment per order is still enforced by the order_id unique constraint).
ALTER TABLE payments ADD COLUMN attempt_count INT NOT NULL DEFAULT 1;
ALTER TABLE payments ADD COLUMN failure_reason VARCHAR(255);

-- A short code is shown to the customer only once an order is ready and verified by the merchant
-- at hand-off. It is nullable for historical orders created before this migration.
ALTER TABLE orders ADD COLUMN pickup_code VARCHAR(12);
