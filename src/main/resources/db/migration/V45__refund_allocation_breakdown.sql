ALTER TABLE service_refunds
    ADD COLUMN inspector_payout_reduction NUMERIC(15, 2) CHECK (inspector_payout_reduction >= 0),
    ADD COLUMN platform_commission_adjustment NUMERIC(15, 2) CHECK (platform_commission_adjustment >= 0),
    ADD COLUMN vat_adjustment NUMERIC(15, 2) CHECK (vat_adjustment >= 0),
    ADD COLUMN tncn_adjustment NUMERIC(15, 2) CHECK (tncn_adjustment >= 0),
    ADD COLUMN platform_absorbed_amount NUMERIC(15, 2) CHECK (platform_absorbed_amount >= 0),
    ADD COLUMN allocation_reconciled_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN allocation_reconciliation_reference VARCHAR(255),
    ADD COLUMN allocation_reconciliation_note VARCHAR(2000),
    ADD COLUMN allocation_reconciled_at TIMESTAMPTZ;

-- Historical refund rows are left NULL: their allocation must be reconstructed from
-- the external accounting evidence instead of inventing a financial split.
-- Keep existing refunded occurrences from being paid out using an unknown allocation.
UPDATE inspector_payouts p
SET payout_held_for_refund = TRUE,
    payout_status = CASE WHEN payout_status = 'ELIGIBLE' THEN 'PENDING' ELSE payout_status END,
    updated_at = CURRENT_TIMESTAMP
FROM service_payments sp
WHERE p.occurrence_id = sp.occurrence_id
  AND sp.refunded_amount > 0
  AND p.payout_status <> 'PAID';

ALTER TABLE service_refunds
    ADD CONSTRAINT ck_service_refunds_allocation_sum
    CHECK (
        (
            inspector_payout_reduction IS NULL
            AND platform_commission_adjustment IS NULL
            AND vat_adjustment IS NULL
            AND tncn_adjustment IS NULL
            AND platform_absorbed_amount IS NULL
            AND allocation_reconciled_by_user_id IS NULL
            AND allocation_reconciliation_reference IS NULL
            AND allocation_reconciliation_note IS NULL
            AND allocation_reconciled_at IS NULL
        )
        OR (
            inspector_payout_reduction IS NOT NULL
            AND platform_commission_adjustment IS NOT NULL
            AND vat_adjustment IS NOT NULL
            AND tncn_adjustment IS NOT NULL
            AND platform_absorbed_amount IS NOT NULL
            AND inspector_payout_reduction
                + platform_commission_adjustment
                + vat_adjustment
                + tncn_adjustment
                + platform_absorbed_amount = amount
            AND (
                allocation_reconciled_by_user_id IS NULL
                AND allocation_reconciliation_reference IS NULL
                AND allocation_reconciliation_note IS NULL
                AND allocation_reconciled_at IS NULL
                OR allocation_reconciled_by_user_id IS NOT NULL
                AND allocation_reconciliation_reference IS NOT NULL
                AND allocation_reconciliation_note IS NOT NULL
                AND allocation_reconciled_at IS NOT NULL
            )
        )
    );
