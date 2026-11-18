-- Track the payment due window and overdue notifications for occurrences awaiting customer payment.
--
-- A report the customer accepts moves its occurrence to PAYMENT_DUE. Without a recorded due date
-- the occurrence sits there indefinitely with no reminder and no escalation. payment_due_at is the
-- deadline the customer is warned against; payment_overdue_notified_at guards against notifying the
-- same customer twice about the same overdue invoice.

ALTER TABLE service_payments
    ADD COLUMN payment_due_at TIMESTAMPTZ,
    ADD COLUMN payment_overdue_notified_at TIMESTAMPTZ;

-- Give existing unpaid occurrences the default 15-day window from their last known write so they
-- are not treated as overdue immediately when this migration is deployed. New acceptances use the
-- configurable application window.
UPDATE service_payments
SET payment_due_at = updated_at + INTERVAL '15 days'
WHERE payment_status = 'PAYMENT_DUE';

CREATE INDEX ix_service_payments_overdue
    ON service_payments (payment_due_at)
    WHERE payment_status = 'PAYMENT_DUE' AND payment_overdue_notified_at IS NULL;