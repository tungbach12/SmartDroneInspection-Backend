-- Track when the open gateway checkout order was created so an abandoned, cancelled or expired
-- VNPay order can be released and the customer can retry instead of being blocked forever.
ALTER TABLE service_payments ADD COLUMN gateway_order_created_at timestamp with time zone;

UPDATE service_payments
SET gateway_order_created_at = updated_at
WHERE payos_order_code IS NOT NULL;
