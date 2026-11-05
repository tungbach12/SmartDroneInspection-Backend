-- Extract per-occurrence finance, customer payment, and Inspector payout data
-- from the scheduling/dispatch table into focused 1:1 tables.

CREATE TABLE service_invoices (
    occurrence_id UUID PRIMARY KEY,
    total_price NUMERIC(15, 2),
    vat_rate NUMERIC(5, 2),
    commission_rate NUMERIC(5, 2),
    tncn_rate NUMERIC(5, 2),
    report_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_service_invoices_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES service_request_occurrences(id) ON DELETE CASCADE
);

CREATE TABLE service_payments (
    occurrence_id UUID PRIMARY KEY,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    payos_order_code BIGINT UNIQUE,
    payment_method VARCHAR(30),
    payment_reference VARCHAR(255),
    payment_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_service_payments_invoice
        FOREIGN KEY (occurrence_id) REFERENCES service_invoices(occurrence_id) ON DELETE CASCADE,
    CONSTRAINT ck_service_payments_status
        CHECK (payment_status IN ('PENDING', 'PAYMENT_DUE', 'PAID'))
);

CREATE TABLE inspector_payouts (
    occurrence_id UUID PRIMARY KEY,
    inspector_id UUID,
    payout_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    payout_reference VARCHAR(255),
    payout_recorded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inspector_payouts_invoice
        FOREIGN KEY (occurrence_id) REFERENCES service_invoices(occurrence_id) ON DELETE CASCADE,
    CONSTRAINT fk_inspector_payouts_inspector
        FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_inspector_payouts_status
        CHECK (payout_status IN ('PENDING', 'ELIGIBLE', 'PAID'))
);

-- Preserve all existing per-occurrence financial and payment state before removing the columns.
INSERT INTO service_invoices (
    occurrence_id, total_price, vat_rate, commission_rate, tncn_rate,
    report_accepted_at, created_at, updated_at
)
SELECT id, total_price, vat_rate, commission_rate, tncn_rate,
       report_accepted_at, created_at, updated_at
FROM service_request_occurrences;

INSERT INTO service_payments (
    occurrence_id, payment_status, payos_order_code, payment_method,
    payment_reference, payment_confirmed_at, created_at, updated_at
)
SELECT id, payment_status, payos_order_code, payment_method,
       payment_reference, payment_confirmed_at, created_at, updated_at
FROM service_request_occurrences;

INSERT INTO inspector_payouts (
    occurrence_id, inspector_id, payout_status, payout_reference, payout_recorded_at, created_at, updated_at
)
SELECT id, inspector_id, payout_status, payout_reference, payout_recorded_at, created_at, updated_at
FROM service_request_occurrences;

DROP INDEX IF EXISTS ix_service_request_occurrences_payment_status;
DROP INDEX IF EXISTS ix_service_request_occurrences_payout_status;

ALTER TABLE service_request_occurrences
    DROP CONSTRAINT IF EXISTS ck_service_request_occurrences_payment_status,
    DROP CONSTRAINT IF EXISTS ck_service_request_occurrences_payout_status,
    DROP COLUMN total_price,
    DROP COLUMN vat_rate,
    DROP COLUMN commission_rate,
    DROP COLUMN tncn_rate,
    DROP COLUMN report_accepted_at,
    DROP COLUMN payment_status,
    DROP COLUMN payos_order_code,
    DROP COLUMN payment_method,
    DROP COLUMN payment_reference,
    DROP COLUMN payment_confirmed_at,
    DROP COLUMN payout_status,
    DROP COLUMN payout_reference,
    DROP COLUMN payout_recorded_at;

CREATE INDEX ix_service_payments_payment_status ON service_payments (payment_status);
CREATE INDEX ix_inspector_payouts_payout_status ON inspector_payouts (payout_status);
