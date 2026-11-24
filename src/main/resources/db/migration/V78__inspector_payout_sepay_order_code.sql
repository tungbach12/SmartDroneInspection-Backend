ALTER TABLE inspector_payouts
    ADD COLUMN IF NOT EXISTS payout_order_code BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_inspector_payouts_order_code
    ON inspector_payouts (payout_order_code)
    WHERE payout_order_code IS NOT NULL;
