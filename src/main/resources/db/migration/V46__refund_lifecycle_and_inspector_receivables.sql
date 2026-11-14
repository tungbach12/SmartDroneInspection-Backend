ALTER TABLE service_refunds
    ALTER COLUMN refund_reference DROP NOT NULL,
    ADD COLUMN request_reference VARCHAR(255),
    ADD COLUMN inspector_receivable_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN platform_absorbed_plan_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN refund_status VARCHAR(24) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN initiated_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN initiated_at TIMESTAMPTZ,
    ADD COLUMN completed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason VARCHAR(2000),
    ADD CONSTRAINT ck_service_refunds_initiated_allocation_plan
      CHECK (
        inspector_receivable_amount >= 0
        AND platform_absorbed_plan_amount >= 0
        AND inspector_receivable_amount + platform_absorbed_plan_amount <= amount
      );

UPDATE service_refunds
SET refund_status = 'COMPLETED',
    completed_by_user_id = recorded_by_user_id,
    completed_at = recorded_at
WHERE refund_status = 'COMPLETED';

ALTER TABLE service_refunds
    ADD CONSTRAINT ck_service_refunds_status
        CHECK (refund_status IN ('INITIATED', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_service_refunds_lifecycle
        CHECK (
            (refund_status = 'INITIATED'
                AND initiated_by_user_id IS NOT NULL AND initiated_at IS NOT NULL
                AND refund_reference IS NULL AND completed_at IS NULL AND cancelled_at IS NULL)
            OR (refund_status = 'COMPLETED'
                AND refund_reference IS NOT NULL AND completed_by_user_id IS NOT NULL
                AND completed_at IS NOT NULL AND cancelled_at IS NULL)
            OR (refund_status = 'CANCELLED'
                AND initiated_by_user_id IS NOT NULL AND initiated_at IS NOT NULL
                AND cancelled_by_user_id IS NOT NULL AND cancelled_at IS NOT NULL
                AND cancellation_reason IS NOT NULL)
        );

CREATE UNIQUE INDEX uq_service_refunds_request_reference_ci
    ON service_refunds (lower(request_reference))
    WHERE request_reference IS NOT NULL;

CREATE TABLE inspector_receivables (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id UUID NOT NULL UNIQUE REFERENCES service_refunds(id) ON DELETE RESTRICT,
    occurrence_id UUID NOT NULL REFERENCES service_request_occurrences(id) ON DELETE RESTRICT,
    inspector_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assessed_amount NUMERIC(15, 2) NOT NULL CHECK (assessed_amount > 0),
    source_reference VARCHAR(255) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE inspector_receivable_recoveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receivable_id UUID NOT NULL REFERENCES inspector_receivables(id) ON DELETE RESTRICT,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    external_reference VARCHAR(255) NOT NULL,
    note VARCHAR(2000) NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_inspector_receivable_recovery_reference_ci
    ON inspector_receivable_recoveries (lower(external_reference));
CREATE INDEX ix_inspector_receivables_inspector_created
    ON inspector_receivables (inspector_id, created_at);
CREATE INDEX ix_inspector_receivable_recoveries_receivable
    ON inspector_receivable_recoveries (receivable_id, recorded_at);
