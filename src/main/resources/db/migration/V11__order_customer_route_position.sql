-- Preserve the customer's order-origin/latest consented position so the customer route can
-- continue seamlessly after refresh. Merchant queue DTOs deliberately do not render these.
ALTER TABLE orders ADD COLUMN customer_latitude DOUBLE;
ALTER TABLE orders ADD COLUMN customer_longitude DOUBLE;
