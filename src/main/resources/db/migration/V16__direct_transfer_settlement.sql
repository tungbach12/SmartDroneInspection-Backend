-- V16: direct-transfer settlement - order states, payment milestones, and invoices for both flows.
--
-- Forward-only: V1..V15 are applied and are not edited here. This adds nullable columns, widens three
-- CHECK constraints (supersets of the previous value lists, so every existing row still passes),
-- relaxes two NOT NULL constraints on invoices, and adds two partial UNIQUE indexes. Nothing is
-- dropped except constraints this file re-declares, and no existing column becomes NOT NULL.
--
-- Authority: business-flows.md v3.3 §III.2 (Direct-Transfer Settlement Lifecycle) and MF4-05 / MF5-07,
-- which are the same four steps for inspection and for maintenance:
--   1. SYSTEM generates the electronic Payment Invoice (contract amount + PROVIDER_MANAGER's bank
--      account)            -> inspection_service_orders.payment_invoice_issued_at /
--                             maintenance_orders.payment_invoice_issued_at, status AWAITING_PAYMENT
--   2. CLIENT transfers 100% of the fee DIRECTLY to the provider's bank account, off platform
--   3. PROVIDER_MANAGER checks its own bank credit and clicks "Confirm receipt"
--                          -> status PAID, paid_at; the provider then sends its VAT service invoice to
--                             the client (invoice_type INSPECTION_SERVICE / MAINTENANCE_SERVICE)
--   4. SYSTEM computes commission C = r x B (r = the rate snapshotted on the order by V15, B = the
--      VAT-exclusive service value of the order) plus VAT on the commission; PLATFORM_OPERATOR issues
--                          -> invoice_type COMMISSION, billed to provider_organization_id, and the
--                             provider pays the platform separately. Commission is never added to the
--                             client's bill.
--
-- MF1-06: the electronic contract takes effect immediately when both parties sign - CONFIRMED means
-- "signed and in force" - and NO payment may exist before acceptance. That is enforced here, not only
-- in the service layer: AWAITING_PAYMENT and PAID both require completed_at (the acceptance instant,
-- see database-design.md inspection_service_orders.completed_at), so a row cannot reach a payment
-- state without an accepted deliverable.
--
-- No money is held by the platform. There is NO escrow, advance-funding, hold, ledger, refund or
-- payment-partner column anywhere in this file or in V15: the platform never touches customer money
-- (Decree 52/2024/NĐ-CP), it orchestrates state and documents only. Retention (H) does not exist; the
-- warranty is a time-based free-rework obligation modelled in V18.
--
-- Bank details: provider_bank_account_number + provider_bank_name only, on the ORDER, because the
-- Payment Invoice (step 1) is an order-level document (payment_invoice_issued_at) and because the
-- client must transfer to exactly the account printed on their invoice. Account numbers are public
-- payment coordinates, never secrets; no token, credential, card or IBAN-authentication value is
-- stored anywhere in this schema.
--
-- Pre-checks: every CHECK added to a populated table below is preceded by a DO block whose predicate is
-- the exact negation of the constraint, written NOT (<predicate identical to the CHECK body>). The two
-- partial UNIQUE indexes carry their pre-deploy violation query in this header and in the block above
-- them. Relaxing a NOT NULL constraint cannot fail on data and needs no pre-check.


-- ============================== inspection_service_orders ==============================

-- 1. Payment and acceptance milestones (MF4-04a/b, MF4-05).
ALTER TABLE inspection_service_orders
    ADD COLUMN IF NOT EXISTS client_review_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS payment_invoice_issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_bank_account_number VARCHAR(34),
    ADD COLUMN IF NOT EXISTS provider_bank_name VARCHAR(200);

COMMENT ON COLUMN inspection_service_orders.client_review_ends_at IS
    'Instant the contractual review window (locked_review_period_days, snapshotted per order) expires. Sweep key for MF4-04b auto-acceptance: no client response and no open complaint by then -> the system accepts per contract terms. Application rule: it is computed from the report release instant plus the snapshotted period; the database stores it, it does not derive it.';
COMMENT ON COLUMN inspection_service_orders.payment_invoice_issued_at IS
    'MF4-05 step 1: when the SYSTEM issued the electronic Payment Invoice showing the contract amount and the provider bank account. Money has not moved at this point.';
COMMENT ON COLUMN inspection_service_orders.paid_at IS
    'MF4-05 step 3: when PROVIDER_MANAGER verified the credit in its own bank account and confirmed receipt, moving the order to PAID. The transfer itself happened bank-to-bank, outside the platform.';
COMMENT ON COLUMN inspection_service_orders.provider_bank_account_number IS
    'Provider payout account number printed on this order''s Payment Invoice. Account number only - never a token, credential or secret. NULL until the Payment Invoice exists.';
COMMENT ON COLUMN inspection_service_orders.provider_bank_name IS
    'Bank of the provider payout account printed on this order''s Payment Invoice. NULL until the Payment Invoice exists.';


-- 2. The order status set. Widened (superset) from V6, so every existing row still satisfies it.
--
--    Lifecycle under the direct-transfer contract:
--      CONFIRMED          MF1-06 both parties signed; contract in force immediately, no advance payment
--      ASSIGNMENT_PENDING inspector assignment outstanding
--      READY_FOR_FLIGHT   MF2-06 mission plan approved and released by PROVIDER_MANAGER
--      READY_FOR_INSPECTION assignment accepted, field work may start
--      IN_PROGRESS        field work / report production under way
--      AWAITING_ACCEPTANCE report released; the snapshotted review window is counting down (MF3-09)
--      COMPLETED          accepted by the client (manually, or by the system under MF4-04b);
--                         completed_at is the acceptance instant
--      AWAITING_PAYMENT   MF4-05.1 Payment Invoice issued, direct transfer pending
--      PAID               MF4-05.3 provider confirmed the credit
--      DISPUTED           MF4-03 an internal complaint is open: acceptance and payment confirmation
--                         are paused. Workflow state only - there are no platform funds to freeze.
--      CANCELLED          cancellation under the snapshotted cancellation policy
--
--    Pre-check (exact negation of the constraint below):
--      SELECT id, status FROM inspection_service_orders
--       WHERE NOT (status IN ('CONFIRMED','ASSIGNMENT_PENDING','READY_FOR_FLIGHT','READY_FOR_INSPECTION',
--                             'IN_PROGRESS','AWAITING_ACCEPTANCE','COMPLETED','AWAITING_PAYMENT','PAID',
--                             'DISPUTED','CANCELLED'));
--    Remediation: none is automatic - an unknown status means the row was written by code that does not
--    belong to this contract. Map it to the intended state by hand, then rerun.
DO $$
DECLARE
    bad_statuses TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_statuses
      FROM (
              SELECT id || ' -> ' || status AS label
                FROM inspection_service_orders
               WHERE NOT (status IN ('CONFIRMED', 'ASSIGNMENT_PENDING', 'READY_FOR_FLIGHT',
                                     'READY_FOR_INSPECTION', 'IN_PROGRESS', 'AWAITING_ACCEPTANCE',
                                     'COMPLETED', 'AWAITING_PAYMENT', 'PAID', 'DISPUTED', 'CANCELLED'))
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_statuses IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: inspection_service_orders.status holds values outside the direct-transfer status set (first 20): %. Remediation: map each row to its intended state by hand, then rerun this migration.',
            bad_statuses;
    END IF;
END $$;

ALTER TABLE inspection_service_orders DROP CONSTRAINT IF EXISTS ck_inspection_service_orders_status;
ALTER TABLE inspection_service_orders
    ADD CONSTRAINT ck_inspection_service_orders_status CHECK (status IN (
        'CONFIRMED', 'ASSIGNMENT_PENDING', 'READY_FOR_FLIGHT', 'READY_FOR_INSPECTION',
        'IN_PROGRESS', 'AWAITING_ACCEPTANCE', 'COMPLETED', 'AWAITING_PAYMENT', 'PAID',
        'DISPUTED', 'CANCELLED'
    ));


-- 3. Acceptance before money, and the milestones that must exist in each payment state.
--    This is the database half of MF1-06 ("payment happens later, after acceptance") and of MF4-05.
--
--    Pre-check (exact negation):
--      SELECT id, status, completed_at, client_review_ends_at, payment_invoice_issued_at, paid_at,
--             locked_review_period_days
--        FROM inspection_service_orders
--       WHERE NOT ( <this CHECK body> );
--    Remediation: the row claims a state its milestones cannot support. Either the milestone is missing
--    (set client_review_ends_at / payment_invoice_issued_at / paid_at to the true instant) or the status
--    is wrong (move the order back to the state its data supports); rerun after correcting.
DO $$
DECLARE
    bad_milestones TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_milestones
      FROM (
              SELECT id || ' -> ' || status
                     || ' (completed_at=' || COALESCE(completed_at::text, 'null')
                     || ', review_ends=' || COALESCE(client_review_ends_at::text, 'null')
                     || ', invoice_issued=' || COALESCE(payment_invoice_issued_at::text, 'null')
                     || ', paid_at=' || COALESCE(paid_at::text, 'null')
                     || ', review_days=' || COALESCE(locked_review_period_days::text, 'null') || ')'
                     AS label
                FROM inspection_service_orders
               WHERE NOT (
                     (status = 'AWAITING_ACCEPTANCE'
                          AND completed_at IS NULL
                          AND client_review_ends_at IS NOT NULL
                          AND locked_review_period_days IS NOT NULL)
                  OR (status = 'AWAITING_PAYMENT'
                          AND completed_at IS NOT NULL
                          AND payment_invoice_issued_at IS NOT NULL)
                  OR (status = 'PAID'
                          AND completed_at IS NOT NULL
                          AND payment_invoice_issued_at IS NOT NULL
                          AND paid_at IS NOT NULL
                          AND paid_at >= completed_at)
                  OR (status NOT IN ('AWAITING_ACCEPTANCE', 'AWAITING_PAYMENT', 'PAID'))
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_milestones IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: inspection_service_orders.status is inconsistent with its acceptance/payment milestones (AWAITING_ACCEPTANCE needs the review window and no acceptance; AWAITING_PAYMENT and PAID need an acceptance instant, PAID needs paid_at not before it) (first 20): %. Remediation: set the missing milestone to the true instant or move the row back to the state its data supports, then rerun.',
            bad_milestones;
    END IF;
END $$;

ALTER TABLE inspection_service_orders DROP CONSTRAINT IF EXISTS ck_inspection_service_orders_settlement;
ALTER TABLE inspection_service_orders
    ADD CONSTRAINT ck_inspection_service_orders_settlement CHECK (
          (status = 'AWAITING_ACCEPTANCE'
               AND completed_at IS NULL
               AND client_review_ends_at IS NOT NULL
               AND locked_review_period_days IS NOT NULL)
       OR (status = 'AWAITING_PAYMENT'
               AND completed_at IS NOT NULL
               AND payment_invoice_issued_at IS NOT NULL)
       OR (status = 'PAID'
               AND completed_at IS NOT NULL
               AND payment_invoice_issued_at IS NOT NULL
               AND paid_at IS NOT NULL
               AND paid_at >= completed_at)
       OR (status NOT IN ('AWAITING_ACCEPTANCE', 'AWAITING_PAYMENT', 'PAID'))
    );


-- 4. The Payment Invoice must show a complete bank account, and the pair is always all-or-nothing.
--
--    Pre-check (exact negation):
--      SELECT id, status, provider_bank_account_number, provider_bank_name
--        FROM inspection_service_orders
--       WHERE NOT ( <this CHECK body> );
--    Remediation: set both bank columns (account number and bank name as printed on the Payment
--    Invoice) or clear both, then rerun.
DO $$
DECLARE
    bad_bank TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_bank
      FROM (
              SELECT id || ' -> ' || status
                     || ' (account=' || COALESCE(provider_bank_account_number, 'null')
                     || ', bank=' || COALESCE(provider_bank_name, 'null') || ')' AS label
                FROM inspection_service_orders
               WHERE NOT (
                     (status NOT IN ('AWAITING_PAYMENT', 'PAID')
                          OR (provider_bank_account_number IS NOT NULL
                              AND provider_bank_name IS NOT NULL
                              AND BTRIM(provider_bank_account_number) <> ''
                              AND BTRIM(provider_bank_name) <> ''))
                 AND (  (provider_bank_account_number IS NULL AND provider_bank_name IS NULL)
                     OR (provider_bank_account_number IS NOT NULL
                         AND provider_bank_name IS NOT NULL
                         AND BTRIM(provider_bank_account_number) <> ''
                         AND BTRIM(provider_bank_name) <> ''))
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_bank IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: inspection_service_orders bank details are missing for a payment state or are not a complete non-blank pair (first 20): %. Remediation: set both provider_bank_account_number and provider_bank_name to the account printed on the Payment Invoice, or set both to NULL, then rerun.',
            bad_bank;
    END IF;
END $$;

ALTER TABLE inspection_service_orders DROP CONSTRAINT IF EXISTS ck_inspection_service_orders_payment_bank;
ALTER TABLE inspection_service_orders
    ADD CONSTRAINT ck_inspection_service_orders_payment_bank CHECK (
        (status NOT IN ('AWAITING_PAYMENT', 'PAID')
             OR (provider_bank_account_number IS NOT NULL
                 AND provider_bank_name IS NOT NULL
                 AND BTRIM(provider_bank_account_number) <> ''
                 AND BTRIM(provider_bank_name) <> ''))
        AND (  (provider_bank_account_number IS NULL AND provider_bank_name IS NULL)
            OR (provider_bank_account_number IS NOT NULL
                AND provider_bank_name IS NOT NULL
                AND BTRIM(provider_bank_account_number) <> ''
                AND BTRIM(provider_bank_name) <> ''))
    );

-- MF4-04b sweep: which orders are inside their contractual review window and due for auto-acceptance.
CREATE INDEX IF NOT EXISTS ix_inspection_service_orders_review_sweep
    ON inspection_service_orders (client_review_ends_at)
    WHERE status = 'AWAITING_ACCEPTANCE';


-- ================================ maintenance_orders ===================================

-- 5. The same settlement milestones for maintenance (MF5-07 is identical to MF4-05).
ALTER TABLE maintenance_orders
    ADD COLUMN IF NOT EXISTS payment_invoice_issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_bank_account_number VARCHAR(34),
    ADD COLUMN IF NOT EXISTS provider_bank_name VARCHAR(200);

COMMENT ON COLUMN maintenance_orders.payment_invoice_issued_at IS
    'MF5-07 step 1: when the SYSTEM issued the Payment Invoice for the accepted works value with the provider bank details.';
COMMENT ON COLUMN maintenance_orders.paid_at IS
    'MF5-07 step 3: when PROVIDER_MANAGER confirmed the credit of the client''s direct transfer and the order became PAID.';
COMMENT ON COLUMN maintenance_orders.provider_bank_account_number IS
    'Provider payout account number printed on this order''s Payment Invoice. Account number only - never a token, credential or secret. NULL until the Payment Invoice exists.';
COMMENT ON COLUMN maintenance_orders.provider_bank_name IS
    'Bank of the provider payout account printed on this order''s Payment Invoice. NULL until the Payment Invoice exists.';


-- 6. Status set widened (superset) from V8.
--
--    Pre-check (exact negation):
--      SELECT id, status FROM maintenance_orders
--       WHERE NOT (status IN ('CONFIRMED','IN_PROGRESS','COMPLETED','AWAITING_PAYMENT','PAID',
--                             'SUPERSEDED','CANCELLED'));
--    Remediation: map each unknown status to its intended state by hand, then rerun.
DO $$
DECLARE
    bad_statuses TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_statuses
      FROM (
              SELECT id || ' -> ' || status AS label
                FROM maintenance_orders
               WHERE NOT (status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'AWAITING_PAYMENT',
                                     'PAID', 'SUPERSEDED', 'CANCELLED'))
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_statuses IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: maintenance_orders.status holds values outside the direct-transfer status set (first 20): %. Remediation: map each row to its intended state by hand, then rerun this migration.',
            bad_statuses;
    END IF;
END $$;

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS ck_maintenance_orders_status;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT ck_maintenance_orders_status CHECK (status IN (
        'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'AWAITING_PAYMENT', 'PAID',
        'SUPERSEDED', 'CANCELLED'
    ));


-- 7. Acceptance before money for maintenance too (MF5-06 completion acceptance, then MF5-07).
--
--    Pre-check (exact negation):
--      SELECT id, status, completed_at, payment_invoice_issued_at, paid_at FROM maintenance_orders
--       WHERE NOT ( <this CHECK body> );
--    Remediation: set the missing milestone to the true instant or move the row back to the state its
--    data supports, then rerun.
DO $$
DECLARE
    bad_milestones TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_milestones
      FROM (
              SELECT id || ' -> ' || status
                     || ' (completed_at=' || COALESCE(completed_at::text, 'null')
                     || ', invoice_issued=' || COALESCE(payment_invoice_issued_at::text, 'null')
                     || ', paid_at=' || COALESCE(paid_at::text, 'null') || ')' AS label
                FROM maintenance_orders
               WHERE NOT (
                     (status = 'AWAITING_PAYMENT'
                          AND completed_at IS NOT NULL
                          AND payment_invoice_issued_at IS NOT NULL)
                  OR (status = 'PAID'
                          AND completed_at IS NOT NULL
                          AND payment_invoice_issued_at IS NOT NULL
                          AND paid_at IS NOT NULL
                          AND paid_at >= completed_at)
                  OR (status NOT IN ('AWAITING_PAYMENT', 'PAID'))
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_milestones IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: maintenance_orders.status is inconsistent with its acceptance/payment milestones (AWAITING_PAYMENT and PAID need an acceptance instant, PAID needs paid_at not before it) (first 20): %. Remediation: set the missing milestone to the true instant or move the row back to the state its data supports, then rerun.',
            bad_milestones;
    END IF;
END $$;

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS ck_maintenance_orders_settlement;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT ck_maintenance_orders_settlement CHECK (
          (status = 'AWAITING_PAYMENT'
               AND completed_at IS NOT NULL
               AND payment_invoice_issued_at IS NOT NULL)
       OR (status = 'PAID'
               AND completed_at IS NOT NULL
               AND payment_invoice_issued_at IS NOT NULL
               AND paid_at IS NOT NULL
               AND paid_at >= completed_at)
       OR (status NOT IN ('AWAITING_PAYMENT', 'PAID'))
    );


-- 8. Same bank-account rule as section 4.
--
--    Pre-check (exact negation):
--      SELECT id, status, provider_bank_account_number, provider_bank_name FROM maintenance_orders
--       WHERE NOT ( <this CHECK body> );
--    Remediation: set both bank columns to the account printed on the Payment Invoice, or clear both.
DO $$
DECLARE
    bad_bank TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_bank
      FROM (
              SELECT id || ' -> ' || status
                     || ' (account=' || COALESCE(provider_bank_account_number, 'null')
                     || ', bank=' || COALESCE(provider_bank_name, 'null') || ')' AS label
                FROM maintenance_orders
               WHERE NOT (
                     (status NOT IN ('AWAITING_PAYMENT', 'PAID')
                          OR (provider_bank_account_number IS NOT NULL
                              AND provider_bank_name IS NOT NULL
                              AND BTRIM(provider_bank_account_number) <> ''
                              AND BTRIM(provider_bank_name) <> ''))
                 AND (  (provider_bank_account_number IS NULL AND provider_bank_name IS NULL)
                     OR (provider_bank_account_number IS NOT NULL
                         AND provider_bank_name IS NOT NULL
                         AND BTRIM(provider_bank_account_number) <> ''
                         AND BTRIM(provider_bank_name) <> ''))
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_bank IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: maintenance_orders bank details are missing for a payment state or are not a complete non-blank pair (first 20): %. Remediation: set both provider_bank_account_number and provider_bank_name to the account printed on the Payment Invoice, or set both to NULL, then rerun.',
            bad_bank;
    END IF;
END $$;

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS ck_maintenance_orders_payment_bank;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT ck_maintenance_orders_payment_bank CHECK (
        (status NOT IN ('AWAITING_PAYMENT', 'PAID')
             OR (provider_bank_account_number IS NOT NULL
                 AND provider_bank_name IS NOT NULL
                 AND BTRIM(provider_bank_account_number) <> ''
                 AND BTRIM(provider_bank_name) <> ''))
        AND (  (provider_bank_account_number IS NULL AND provider_bank_name IS NULL)
            OR (provider_bank_account_number IS NOT NULL
                AND provider_bank_name IS NOT NULL
                AND BTRIM(provider_bank_account_number) <> ''
                AND BTRIM(provider_bank_name) <> ''))
    );


-- ==================================== invoices =========================================
--
-- V8 declared invoices as maintenance-only: maintenance_order_id and maintenance_ticket_id were both
-- NOT NULL and no discriminator existed. Under MF4-05/MF5-07 the same table carries three kinds:
--
--   MAINTENANCE_SERVICE  provider's VAT service invoice for accepted maintenance work (V8 behaviour)
--   INSPECTION_SERVICE   provider's VAT service invoice for an accepted inspection report (MF4-05.3)
--   COMMISSION           the PLATFORM's own service invoice: commission C = r x B plus VAT on the
--                        commission, billed to the provider organization (MF4-05.4 / MF5-07.4)
--
-- The Payment Invoice of MF4-05.1 is NOT a row here: it is an order-level document whose issuance is
-- recorded by inspection_service_orders.payment_invoice_issued_at / maintenance_orders.payment_invoice_
-- issued_at together with the bank account it prints. Provider VAT invoices and platform commission
-- invoices are the rows that live in this table.
--
-- organization_id keeps its NOT NULL from V8. For the two service kinds it is the billed customer
-- organization; for COMMISSION it carries the customer organization of the underlying order, for
-- traceability, while the billed party is provider_organization_id.
-- maintenance_ticket_id keeps its V8 value for maintenance rows and is NULL for inspection rows.

-- 9. The discriminator and the two new references.
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(32) NOT NULL DEFAULT 'MAINTENANCE_SERVICE',
    ADD COLUMN IF NOT EXISTS inspection_service_order_id UUID,
    ADD COLUMN IF NOT EXISTS provider_organization_id UUID;

-- Relax the maintenance-only NOT NULLs: an inspection invoice has no maintenance order or ticket, and
-- a commission invoice for an inspection order has neither. Relaxing a constraint cannot fail on data.
ALTER TABLE invoices ALTER COLUMN maintenance_order_id DROP NOT NULL;
ALTER TABLE invoices ALTER COLUMN maintenance_ticket_id DROP NOT NULL;

COMMENT ON COLUMN invoices.invoice_type IS
    'MAINTENANCE_SERVICE / INSPECTION_SERVICE: the provider''s VAT service invoice recorded on the platform. COMMISSION: the platform''s own invoice for C = r x B plus VAT on commission, billed to the provider. No escrow, hold or funding invoice kind exists.';
COMMENT ON COLUMN invoices.inspection_service_order_id IS
    'Set for INSPECTION_SERVICE and inspection-side COMMISSION invoices; mutually exclusive with maintenance_order_id.';
COMMENT ON COLUMN invoices.provider_organization_id IS
    'Billed party of a COMMISSION invoice: the Provider Organization the commission was computed from. NULL for service invoices, which are billed to organization_id.';


-- 10. Pre-check for the two new foreign keys (exact negation of each, OR-ed; each column is new in this
--     file so every branch is provably empty on a V15 database - stated, not assumed):
--
--     SELECT 'inspection_service_order' AS which, id::text FROM invoices
--      WHERE NOT (inspection_service_order_id IS NULL
--                 OR EXISTS (SELECT 1 FROM inspection_service_orders o WHERE o.id = inspection_service_order_id))
--     UNION ALL
--     SELECT 'provider_organization', id::text FROM invoices
--      WHERE NOT (provider_organization_id IS NULL
--                 OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_organization_id));
--
--     Remediation: clear the unattributable reference or create the target row first, then rerun.
DO $$
DECLARE
    dangling TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO dangling
      FROM (
              SELECT 'inspection_service_order ' || id AS label
                FROM invoices
               WHERE NOT (inspection_service_order_id IS NULL
                           OR EXISTS (SELECT 1
                                        FROM inspection_service_orders o
                                       WHERE o.id = inspection_service_order_id))
              UNION ALL
              SELECT 'provider_organization ' || id
                FROM invoices
               WHERE NOT (provider_organization_id IS NULL
                           OR EXISTS (SELECT 1
                                        FROM provider_organizations p
                                       WHERE p.id = provider_organization_id))
              ORDER BY 1
              LIMIT 20
           ) offender;

    IF dangling IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: an invoice references an inspection order or a provider organization that does not exist (first 20): %. Remediation: UPDATE invoices SET inspection_service_order_id = NULL / provider_organization_id = NULL WHERE id = <id>, or create the target row first, then rerun.',
            dangling;
    END IF;
END $$;

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoices_inspection_service_order
        FOREIGN KEY (inspection_service_order_id)
        REFERENCES inspection_service_orders (id) ON DELETE RESTRICT;

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoices_provider_organization
        FOREIGN KEY (provider_organization_id)
        REFERENCES provider_organizations (id) ON DELETE RESTRICT;


-- 11. Pre-check for the type vocabulary AND the order binding, which one CHECK states together: an
--     unknown invoice_type matches no branch and is therefore refused by the same constraint.
--
--     Pre-deploy query (must return zero rows):
--       SELECT id, invoice_type, maintenance_order_id, inspection_service_order_id,
--              provider_organization_id
--         FROM invoices
--        WHERE NOT ( <this CHECK body> );
--
--     Remediation: every pre-V16 row is a maintenance service invoice (the DEFAULT above), so a row can
--     only fail if it already names an inspection order or a provider organization without the matching
--     type. Set invoice_type to the kind the row actually is, or clear the reference that does not
--     belong to it, then rerun.
DO $$
DECLARE
    bad_bindings TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_bindings
      FROM (
              SELECT id || ' -> ' || invoice_type
                     || ' (maintenance_order=' || COALESCE(maintenance_order_id::text, 'null')
                     || ', inspection_order=' || COALESCE(inspection_service_order_id::text, 'null')
                     || ', provider=' || COALESCE(provider_organization_id::text, 'null') || ')'
                     AS label
                FROM invoices
               WHERE NOT (
                     invoice_type IN ('MAINTENANCE_SERVICE', 'INSPECTION_SERVICE', 'COMMISSION')
                     AND (
                          (invoice_type = 'MAINTENANCE_SERVICE'
                               AND maintenance_order_id IS NOT NULL
                               AND inspection_service_order_id IS NULL)
                       OR (invoice_type = 'INSPECTION_SERVICE'
                               AND inspection_service_order_id IS NOT NULL
                               AND maintenance_order_id IS NULL)
                       OR (invoice_type = 'COMMISSION'
                               AND provider_organization_id IS NOT NULL
                               AND (  (maintenance_order_id IS NOT NULL
                                       AND inspection_service_order_id IS NULL)
                                   OR (inspection_service_order_id IS NOT NULL
                                       AND maintenance_order_id IS NULL)))
                     )
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_bindings IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: invoices.invoice_type is unknown, or does not match the order the invoice is bound to (maintenance invoices need a maintenance order and no inspection order, inspection invoices the reverse, commission invoices exactly one order plus the provider organization) (first 20): %. Remediation: set invoice_type to the kind the row actually is and clear the reference that does not belong to it, then rerun.',
            bad_bindings;
    END IF;
END $$;

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS ck_invoices_type_and_order;
ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_type_and_order CHECK (
        invoice_type IN ('MAINTENANCE_SERVICE', 'INSPECTION_SERVICE', 'COMMISSION')
        AND (
             (invoice_type = 'MAINTENANCE_SERVICE'
                  AND maintenance_order_id IS NOT NULL
                  AND inspection_service_order_id IS NULL)
          OR (invoice_type = 'INSPECTION_SERVICE'
                  AND inspection_service_order_id IS NOT NULL
                  AND maintenance_order_id IS NULL)
          OR (invoice_type = 'COMMISSION'
                  AND provider_organization_id IS NOT NULL
                  AND (  (maintenance_order_id IS NOT NULL AND inspection_service_order_id IS NULL)
                      OR (inspection_service_order_id IS NOT NULL AND maintenance_order_id IS NULL)))
        )
    );

CREATE INDEX IF NOT EXISTS ix_invoices_inspection_service_order
    ON invoices (inspection_service_order_id)
    WHERE inspection_service_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_invoices_provider_organization
    ON invoices (provider_organization_id)
    WHERE provider_organization_id IS NOT NULL;


-- 12. Commission is applied ONCE per order (contract invariant: commission applies once; a retention
--     release earns no second commission - and retention does not exist at all here). A VOID invoice is
--     excluded so the platform can withdraw and reissue one, which is the normal correction path.
--
--     UNIQUE INDEX on the populated invoices table. Pre-deploy violation query for BOTH indexes (each
--     must return zero rows):
--
--       SELECT inspection_service_order_id, count(*) FROM invoices
--        WHERE invoice_type = 'COMMISSION' AND inspection_service_order_id IS NOT NULL
--          AND status <> 'VOID'
--        GROUP BY inspection_service_order_id HAVING count(*) > 1;
--
--       SELECT maintenance_order_id, count(*) FROM invoices
--        WHERE invoice_type = 'COMMISSION' AND maintenance_order_id IS NOT NULL
--          AND status <> 'VOID'
--        GROUP BY maintenance_order_id HAVING count(*) > 1;
--
--     Remediation: more than one live commission invoice for one order means the commission was billed
--     twice. VOID the surplus rows (UPDATE invoices SET status = 'VOID' WHERE id = <id>) keeping exactly
--     one, then rerun.
DO $$
DECLARE
    duplicated TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO duplicated
      FROM (
              SELECT 'inspection_service_order ' || inspection_service_order_id
                     || ' x' || count(*) AS label
                FROM invoices
               WHERE invoice_type = 'COMMISSION'
                 AND inspection_service_order_id IS NOT NULL
                 AND status <> 'VOID'
               GROUP BY inspection_service_order_id
              HAVING count(*) > 1
              UNION ALL
              SELECT 'maintenance_order ' || maintenance_order_id || ' x' || count(*)
                FROM invoices
               WHERE invoice_type = 'COMMISSION'
                 AND maintenance_order_id IS NOT NULL
                 AND status <> 'VOID'
               GROUP BY maintenance_order_id
              HAVING count(*) > 1
              ORDER BY 1
              LIMIT 20
           ) offender;

    IF duplicated IS NOT NULL THEN
        RAISE EXCEPTION
            'V16 precheck failed: more than one non-VOID COMMISSION invoice exists for the same order, and the unique indexes uq_invoices_commission_inspection / uq_invoices_commission_maintenance would reject them (first 20): %. Remediation: keep exactly one commission invoice per order and VOID the surplus (UPDATE invoices SET status = ''VOID'' WHERE id = <id>), then rerun.',
            duplicated;
    END IF;
END $$;

CREATE UNIQUE INDEX uq_invoices_commission_inspection
    ON invoices (inspection_service_order_id)
    WHERE invoice_type = 'COMMISSION'
      AND inspection_service_order_id IS NOT NULL
      AND status <> 'VOID';

CREATE UNIQUE INDEX uq_invoices_commission_maintenance
    ON invoices (maintenance_order_id)
    WHERE invoice_type = 'COMMISSION'
      AND maintenance_order_id IS NOT NULL
      AND status <> 'VOID';
