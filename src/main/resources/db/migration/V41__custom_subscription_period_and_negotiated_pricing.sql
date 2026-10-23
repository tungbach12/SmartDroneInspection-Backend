-- Allow custom billing periods (1 to 120 months) and store negotiated contract pricing.

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_billing_period;

ALTER TABLE subscriptions ALTER COLUMN billing_period_months TYPE INTEGER;

ALTER TABLE subscriptions ADD CONSTRAINT ck_subscriptions_billing_period
    CHECK (billing_period_months >= 1 AND billing_period_months <= 120);

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS agreed_amount_usd NUMERIC(12, 2);
