-- V8 — Canonical money fields (minor units + currency) while keeping existing decimal fields for compatibility
ALTER TABLE menu_items ADD COLUMN price_minor BIGINT;
ALTER TABLE menu_items ADD COLUMN currency VARCHAR(3);
UPDATE menu_items SET price_minor = ROUND(price * 100), currency = 'INR' WHERE price_minor IS NULL;

ALTER TABLE order_items ADD COLUMN price_each_minor BIGINT;
ALTER TABLE order_items ADD COLUMN currency VARCHAR(3);
UPDATE order_items SET price_each_minor = ROUND(price_each * 100), currency = 'INR' WHERE price_each_minor IS NULL;

ALTER TABLE orders ADD COLUMN total_amount_minor BIGINT;
ALTER TABLE orders ADD COLUMN currency VARCHAR(3);
UPDATE orders SET total_amount_minor = ROUND(total_amount * 100), currency = 'INR' WHERE total_amount_minor IS NULL;

ALTER TABLE payments ADD COLUMN amount_minor BIGINT;
ALTER TABLE payments ADD COLUMN currency VARCHAR(3);
UPDATE payments SET amount_minor = ROUND(amount * 100), currency = 'INR' WHERE amount_minor IS NULL;
