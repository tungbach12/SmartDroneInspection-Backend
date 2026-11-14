ALTER TABLE service_payments
    ADD COLUMN refunded_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN refund_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    ADD CONSTRAINT ck_service_payments_refund_status
        CHECK (refund_status IN ('NONE', 'PARTIAL', 'FULL')),
    ADD CONSTRAINT ck_service_payments_refunded_amount
        CHECK (refunded_amount >= 0);

ALTER TABLE inspector_payouts
    ADD COLUMN payout_held_for_refund BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN refund_reconciliation_reference VARCHAR(255),
    ADD COLUMN refund_reconciliation_note VARCHAR(2000),
    ADD COLUMN refund_reconciled_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN refund_reconciled_at TIMESTAMPTZ,
    ADD COLUMN payout_recorded_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN final_payout_amount NUMERIC(15, 2) CHECK (final_payout_amount >= 0);

CREATE TABLE service_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurrence_id UUID NOT NULL REFERENCES service_payments(occurrence_id) ON DELETE RESTRICT,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    refund_reference VARCHAR(255) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_service_refunds_reference_ci
    ON service_refunds (lower(refund_reference));

CREATE INDEX ix_service_refunds_occurrence_recorded_at
    ON service_refunds (occurrence_id, recorded_at);
