-- Table for PayPal Orders v2 and online subscription settlement tracking (Report 3 SRS §3.2.3, §4.1, database-design.md §6.1).

CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE RESTRICT,
    paypal_order_id VARCHAR(64) NOT NULL,
    paypal_capture_id VARCHAR(64),
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(24) NOT NULL,
    payer_email VARCHAR(255),
    captured_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_transactions_capture UNIQUE (paypal_capture_id),
    CONSTRAINT ck_payment_transactions_status CHECK (status IN ('COMPLETED', 'PENDING', 'DECLINED', 'REFUNDED'))
);

CREATE INDEX IF NOT EXISTS ix_payment_transactions_subscription ON payment_transactions (subscription_id);
CREATE INDEX IF NOT EXISTS ix_payment_transactions_order ON payment_transactions (paypal_order_id);
