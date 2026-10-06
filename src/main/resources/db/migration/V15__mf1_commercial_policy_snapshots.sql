-- V15: versioned platform commercial policies and the per-order contract snapshot (MF1-05).
--
-- Forward-only: V1..V14 are applied and are not edited here. This creates one table and adds nullable
-- columns to two existing tables. No column becomes NOT NULL, no row is rewritten, nothing is dropped.
--
-- Authority: business-flows.md v3.3 §III.3 - no marketplace parameter is hard-coded. PLATFORM_OPERATOR
-- publishes versioned policies; at signing every accepted value is COPIED INTO THE ORDER ROW and later
-- policy changes apply prospectively only, never rewriting an active contract.
--   r    = platform commission rate   -> platform_configurations STANDARD_COMMISSION,
--          snapshotted to locked_commission_rate (settlement reads the snapshot, never the live policy)
--   T_rev= acceptance review period   -> CLIENT_REVIEW_PERIOD,
--          snapshotted to locked_review_period_days (example value 7 days, per-order, NOT a global
--          default) and the basis for contractual auto-acceptance MF4-04b
--   T_war= standard warranty duration -> WARRANTY_DURATION (snapshotted onto maintenance orders in V18)
--          cancellation conditions    -> CANCELLATION,
--          snapshotted to locked_cancellation_policy as immutable JSON
--
-- DELIBERATELY ABSENT: locked_advance_funding_rate, funding_policy_version, locked_retention_rate,
-- retention_policy_version, retention_amount, retention_status, escrow, hold, payment-partner or
-- ledger columns of any kind. v3.3 removed advance funding, retention (H) and partner escrow entirely:
-- the platform is never a custodian of money (Decree 52/2024/NĐ-CP), so no policy key can even be
-- published for them - ck_platform_configurations_policy_key is an allow-list of exactly the four
-- contract parameters above. See business-flows.md v3.3 §III.3 "Removed in v3.3".
--
-- Nullability: locked_* columns are nullable because every pre-V15 row predates them and a CHECK is
-- validated against existing rows when it is created. The application must write them for every order
-- confirmed after this migration (application rule, stated here because the database cannot express
-- "written after V15"); the state machine in V16 additionally refuses AWAITING_ACCEPTANCE without a
-- snapshotted review period, which is where the snapshot stops being advisory.
--
-- Pre-checks: platform_configurations is brand new, so no pre-existing row can violate anything
-- declared on it - stated, not assumed; its pre-check queries would not even compile before this file.
-- The three CHECKs below DO sit on populated tables and each is preceded by a DO block whose predicate
-- is the exact negation of the constraint, written NOT (<predicate>). All three are provably empty on a
-- V14 database because the columns are created by this very file; they are written so the file is also
-- safe to apply over a database where an operator backfilled a snapshot by hand.


-- 1. Versioned commercial policy records, append-only after publication.
CREATE TABLE platform_configurations
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key         VARCHAR(96) NOT NULL,
    version            INTEGER     NOT NULL,
    policy_value       JSONB       NOT NULL,
    status             VARCHAR(24) NOT NULL,
    effective_from     TIMESTAMPTZ NOT NULL,
    effective_until    TIMESTAMPTZ,
    published_by_user_id UUID      NOT NULL,
    published_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_platform_configurations_key_version UNIQUE (policy_key, version),
    CONSTRAINT fk_platform_configurations_published_by
        FOREIGN KEY (published_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    -- Exactly the four contract parameters, and nothing else. This is the structural half of "no
    -- escrow, no advance funding, no retention": a funding, hold, partner or retention policy cannot be
    -- published at all, and a new policy kind requires a reviewed forward migration rather than a
    -- config write. Values are never defaulted: policy_value is validated against policy_key by the
    -- application, and no numeric default exists for r, T_rev, T_war or the cancellation terms.
    CONSTRAINT ck_platform_configurations_policy_key CHECK (
        policy_key IN ('STANDARD_COMMISSION', 'CLIENT_REVIEW_PERIOD', 'CANCELLATION', 'WARRANTY_DURATION')
    ),
    CONSTRAINT ck_platform_configurations_version CHECK (version > 0),
    CONSTRAINT ck_platform_configurations_status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_platform_configurations_effective CHECK (
        effective_until IS NULL OR effective_until > effective_from
    )
);

COMMENT ON TABLE platform_configurations IS
    'Versioned commercial policies (commission rate r, acceptance review period T_rev, warranty duration T_war, cancellation terms) published prospectively by PLATFORM_OPERATOR. No funding, escrow, hold or retention policy key exists: the platform holds no funds.';

CREATE INDEX ix_platform_configurations_key_status
    ON platform_configurations (policy_key, status, effective_from DESC);

-- One PUBLISHED version per key: settlement and contract snapshot must resolve to exactly one answer
-- for "what is the current rate", and superseding is a status change, not a second live row.
-- New table, therefore empty: a unique index cannot fail on data that does not exist yet.
CREATE UNIQUE INDEX uq_platform_configurations_published
    ON platform_configurations (policy_key)
    WHERE status = 'PUBLISHED';


-- 2. The contract snapshot on an inspection service order (MF1-05 / MF4-04b / MF4-05.4).
ALTER TABLE inspection_service_orders
    ADD COLUMN IF NOT EXISTS locked_commission_rate NUMERIC(7,5),
    ADD COLUMN IF NOT EXISTS locked_review_period_days INTEGER,
    ADD COLUMN IF NOT EXISTS locked_cancellation_policy JSONB,
    ADD COLUMN IF NOT EXISTS shot_list_snapshot JSONB,
    ADD COLUMN IF NOT EXISTS commission_policy_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS review_policy_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cancellation_policy_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS locked_terms_snapshot JSONB;

COMMENT ON COLUMN inspection_service_orders.locked_commission_rate IS
    'Uniform platform commission rate r accepted for this order, copied from its policy version at signing. Settlement computes C = r * B from THIS column and never reads the live policy.';
COMMENT ON COLUMN inspection_service_orders.locked_review_period_days IS
    'Client review period in days, snapshotted per order from the accepted CLIENT_REVIEW_PERIOD policy (example: 7). Not a global default: MF4-04b auto-acceptance uses this value and no other.';
COMMENT ON COLUMN inspection_service_orders.locked_cancellation_policy IS
    'Accepted cancellation conditions, windows and eligible-cost rules as an immutable order snapshot; no global numeric default.';
COMMENT ON COLUMN inspection_service_orders.shot_list_snapshot IS
    'Contract-time snapshot of the agreed technical mission parameters (target GSD, overlap, shot list, camera angles) from MF1-05; the executable mission plan lives in drone_mission_plans (V17).';
COMMENT ON COLUMN inspection_service_orders.locked_terms_snapshot IS
    'Complete immutable snapshot of accepted commercial terms, display labels and policy version identifiers used to construct the electronic order. Contains no advance-funding, escrow or retention term: those do not exist in the contract.';


-- 3. Pre-check: the commission rate snapshotted onto an order must be a rate (0 <= r <= 1).
--
--    SELECT id, locked_commission_rate FROM inspection_service_orders
--     WHERE NOT (locked_commission_rate IS NULL
--                OR (locked_commission_rate >= 0 AND locked_commission_rate <= 1));
--
--    Remediation: no automatic repair - a rate outside [0,1] is not a rate. Correct the row to the
--    rate the parties actually signed (UPDATE inspection_service_orders SET locked_commission_rate =
--    <value> WHERE id = <id>), or clear it if the order predates V15, then rerun.
DO $$
DECLARE
    bad_rates TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_rates
      FROM (
              SELECT id || ' -> ' || locked_commission_rate AS label
                FROM inspection_service_orders
               WHERE NOT (locked_commission_rate IS NULL
                           OR (locked_commission_rate >= 0 AND locked_commission_rate <= 1))
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_rates IS NOT NULL THEN
        RAISE EXCEPTION
            'V15 precheck failed: inspection_service_orders.locked_commission_rate must be NULL or a commission rate between 0 and 1, but these rows hold something else (first 20): %. Remediation: set the column to the rate actually signed, or NULL for a pre-V15 order, then rerun.',
            bad_rates;
    END IF;
END $$;

ALTER TABLE inspection_service_orders DROP CONSTRAINT IF EXISTS ck_inspection_service_orders_commission_rate;
ALTER TABLE inspection_service_orders
    ADD CONSTRAINT ck_inspection_service_orders_commission_rate CHECK (
        locked_commission_rate IS NULL
        OR (locked_commission_rate >= 0 AND locked_commission_rate <= 1)
    );


-- 4. Pre-check: the snapshotted review period is a non-negative number of days (0 means the parties
--    agreed no review window; it is never defaulted to 7 here or anywhere else).
--
--    SELECT id, locked_review_period_days FROM inspection_service_orders
--     WHERE NOT (locked_review_period_days IS NULL OR locked_review_period_days >= 0);
--
--    Remediation: correct the row to the agreed review period, or NULL for a pre-V15 order, then rerun.
DO $$
DECLARE
    bad_periods TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_periods
      FROM (
              SELECT id || ' -> ' || locked_review_period_days AS label
                FROM inspection_service_orders
               WHERE NOT (locked_review_period_days IS NULL OR locked_review_period_days >= 0)
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_periods IS NOT NULL THEN
        RAISE EXCEPTION
            'V15 precheck failed: inspection_service_orders.locked_review_period_days must be NULL or a non-negative day count, but these rows hold something else (first 20): %. Remediation: set the column to the agreed review period for that order, or NULL for a pre-V15 order, then rerun.',
            bad_periods;
    END IF;
END $$;

ALTER TABLE inspection_service_orders DROP CONSTRAINT IF EXISTS ck_inspection_service_orders_review_period;
ALTER TABLE inspection_service_orders
    ADD CONSTRAINT ck_inspection_service_orders_review_period CHECK (
        locked_review_period_days IS NULL OR locked_review_period_days >= 0
    );


-- 5. The same commission snapshot on a maintenance order (MF5-03 contract snapshot). Warranty columns
--    are NOT here: they are added with the warranty clock in V18, so that every MF5-08 rule lives in
--    one file.
ALTER TABLE maintenance_orders
    ADD COLUMN IF NOT EXISTS locked_commission_rate NUMERIC(7,5),
    ADD COLUMN IF NOT EXISTS commission_policy_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS locked_terms_snapshot JSONB;

COMMENT ON COLUMN maintenance_orders.locked_commission_rate IS
    'Uniform platform commission rate r accepted for this repair contract, copied from its policy version at signing; settlement reads this snapshot, never the live policy.';
COMMENT ON COLUMN maintenance_orders.locked_terms_snapshot IS
    'Immutable snapshot of accepted maintenance terms and policy version identifiers. Contains no advance-funding, escrow or retention term: retention (H) does not exist in the contract.';


-- 6. Pre-check, same predicate as section 3:
--
--    SELECT id, locked_commission_rate FROM maintenance_orders
--     WHERE NOT (locked_commission_rate IS NULL
--                OR (locked_commission_rate >= 0 AND locked_commission_rate <= 1));
--
--    Remediation: set the column to the signed rate, or NULL for a pre-V15 order, then rerun.
DO $$
DECLARE
    bad_rates TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_rates
      FROM (
              SELECT id || ' -> ' || locked_commission_rate AS label
                FROM maintenance_orders
               WHERE NOT (locked_commission_rate IS NULL
                           OR (locked_commission_rate >= 0 AND locked_commission_rate <= 1))
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_rates IS NOT NULL THEN
        RAISE EXCEPTION
            'V15 precheck failed: maintenance_orders.locked_commission_rate must be NULL or a commission rate between 0 and 1, but these rows hold something else (first 20): %. Remediation: set the column to the rate actually signed, or NULL for a pre-V15 order, then rerun.',
            bad_rates;
    END IF;
END $$;

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS ck_maintenance_orders_commission_rate;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT ck_maintenance_orders_commission_rate CHECK (
        locked_commission_rate IS NULL
        OR (locked_commission_rate >= 0 AND locked_commission_rate <= 1)
    );
