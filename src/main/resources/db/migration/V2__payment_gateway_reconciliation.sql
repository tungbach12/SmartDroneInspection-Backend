-- Gateway order reconciliation: track when an unpaid checkout order was opened so the
-- reconciliation job can find stale orders whose webhook never arrived, and record the last
-- time the gateway was queried for that order.

ALTER TABLE service_payments
    ADD COLUMN gateway_order_created_at TIMESTAMPTZ,
    ADD COLUMN last_reconciled_at TIMESTAMPTZ;

-- Existing unpaid orders are dated from their last known write; they become reconciliation
-- candidates once the configured grace period has passed.
UPDATE service_payments
SET gateway_order_created_at = updated_at
WHERE payos_order_code IS NOT NULL;

CREATE INDEX ix_service_payments_reconcile_pending
    ON service_payments (gateway_order_created_at)
    WHERE payment_status = 'PAYMENT_DUE' AND payos_order_code IS NOT NULL;
