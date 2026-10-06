-- V20: MF4 internal complaint records (dispute_tickets) with evidence folded into JSONB.
--
-- Forward-only: V1..V19 are applied and are not edited here. This file creates one table and its
-- indexes. It drops nothing, rewrites no row and makes no existing column NOT NULL, so every
-- pre-V20 database migrates without touching data.
--
-- Authority: database-design.md section 6.5 is the schema contract for this table and is followed
-- column for column. The complaint flow (MF4-03) lets either party open a case; PLATFORM_OPERATOR
-- then arbitrates. The evidence column is a JSONB array rather than a separate dispute_evidence
-- table because the contract owner folded it deliberately (a handful of attachments per case does
-- not justify a table); each element carries its own sha-256 so per-file integrity still holds.
--
-- Freeze semantics are WORKFLOW-LEVEL ONLY. Opening a dispute pauses acceptance and the payment
-- sequence by moving the order into status DISPUTED (the value V16 already admits in
-- inspection_service_orders.status and maintenance_orders.status); the order row itself is not
-- touched by this file, and no trigger is attached. There is no money hold of any kind: the
-- platform never holds funds, so nothing here (and nothing in V12..V19) reserves, withholds or
-- moves money. The optional penalty_amount is a contractually/lawfully grounded figure recorded for
-- reference, never an automatic deduction.
--
-- Why order_id has no foreign key. The column is deliberately polymorphic - it names an inspection
-- service order or a maintenance order, discriminated by order_type - and PostgreSQL cannot declare
-- one FK across two tables (a CHECK cannot use a subquery, and a trigger would add schema objects
-- that database-design.md section 6.5 does not list). The pairing of order_type with a real order is
-- therefore owned by the service layer that will create disputes, exactly as database-design.md
-- documents the column pair. This limitation is stated here rather than left implicit.
--
-- Pre-check: none is needed, and this is stated rather than assumed. The table is new, so every
-- constraint below is validated against zero rows, and every foreign key points at tables that have
-- existed since V3 (users, organizations) and V12 (provider_organizations). The test
-- CutListMigrationOnPopulatedDatabaseTest asserts exactly that: the file applies over a database
-- seeded with pre-V20 rows and leaves every one of them untouched.
--
-- Delete behaviour: every reference uses ON DELETE RESTRICT, the convention V6 and V8 already use
-- for commercial history. A dispute case names the parties and the order it was opened on; deleting
-- a user, a customer organization or a provider organization out from under an open or closed case
-- is not something the schema should make easy.

CREATE TABLE dispute_tickets
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_number          VARCHAR(64)   NOT NULL,
    order_id                UUID          NOT NULL,
    order_type              VARCHAR(32)   NOT NULL,
    raised_by_user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    client_organization_id  UUID          NOT NULL REFERENCES organizations (id) ON DELETE RESTRICT,
    provider_organization_id UUID         NOT NULL REFERENCES provider_organizations (id) ON DELETE RESTRICT,
    category                VARCHAR(32)   NOT NULL,
    reason                  VARCHAR(4000) NOT NULL,
    client_claim            VARCHAR(4000),
    provider_response       VARCHAR(4000),
    status                  VARCHAR(32)   NOT NULL,
    resolution_decision     VARCHAR(32),
    resolved_by_operator_id UUID          REFERENCES users (id) ON DELETE RESTRICT,
    resolved_at             TIMESTAMPTZ,
    resolution_notes        VARCHAR(4000),
    penalty_amount          NUMERIC(14,2),
    evidence                JSONB,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_dispute_tickets_number UNIQUE (dispute_number),
    CONSTRAINT ck_dispute_tickets_number CHECK (BTRIM(dispute_number) <> ''),
    CONSTRAINT ck_dispute_tickets_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_dispute_tickets_order_type CHECK (order_type IN ('INSPECTION', 'MAINTENANCE')),
    CONSTRAINT ck_dispute_tickets_category CHECK (
        category IN ('QUALITY_DEFECT', 'MISSING_SHOTS', 'AIRSPACE_SAFETY', 'TIMELINESS', 'BILLING')
    ),
    CONSTRAINT ck_dispute_tickets_status CHECK (
        status IN ('OPENED', 'UNDER_ARBITRATION', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT ck_dispute_tickets_resolution_decision CHECK (
        resolution_decision IS NULL
        OR resolution_decision IN ('FREE_RESHOOT', 'FULL_REFUND', 'REJECTED_DISPUTE')
    ),
    -- An operator and the instant they concluded the case travel together, the same pairing
    -- V14 already enforces for a vetting approval: half a resolution is not a resolution.
    CONSTRAINT ck_dispute_tickets_resolution_pair CHECK (
        (resolved_by_operator_id IS NULL AND resolved_at IS NULL)
        OR (resolved_by_operator_id IS NOT NULL AND resolved_at IS NOT NULL)
    ),
    -- Resolution substance belongs to a case that has one: decision, notes, operator and instant may
    -- only be present once the case is RESOLVED or CLOSED. The converse - that every RESOLVED or
    -- CLOSED row must carry a decision - is deliberately NOT a CHECK, for the same reason V15 keeps
    -- its standing-reason rule one-directional: a CHECK is validated against every row at creation
    -- time and cannot distinguish a case closed on its merits from one closed after withdrawal, so
    -- the always-taken-outcome rule belongs to the aggregate that writes the transition.
    CONSTRAINT ck_dispute_tickets_resolution_state CHECK (
        (resolution_decision IS NULL
            AND resolved_by_operator_id IS NULL
            AND resolved_at IS NULL
            AND resolution_notes IS NULL)
        OR status IN ('RESOLVED', 'CLOSED')
    ),
    CONSTRAINT ck_dispute_tickets_penalty CHECK (penalty_amount IS NULL OR penalty_amount >= 0),
    -- Attachments are an array of objects, never a bare object or scalar. The element shape
    -- ({evidence_type, object_key, checksum_sha256, uploaded_by_user_id, description, created_at})
    -- cannot be checked without a subquery, which PostgreSQL forbids inside CHECK; the type guard
    -- below is the part the database can enforce on its own.
    CONSTRAINT ck_dispute_tickets_evidence CHECK (
        evidence IS NULL OR jsonb_typeof(evidence) = 'array'
    )
);

COMMENT ON TABLE dispute_tickets IS
    'MF4 internal complaint record: either party opens a case, PLATFORM_OPERATOR arbitrates under the published Platform Terms. Opening the case pauses acceptance and payment by moving the ORDER to status DISPUTED; no money is ever held, reserved or withheld by the platform.';
COMMENT ON COLUMN dispute_tickets.order_id IS
    'Inspection service order or maintenance order the case was opened on, discriminated by order_type. Polymorphic by design, so no foreign key: database-design.md section 6.5 documents the pair and the service layer owns that a real order is named.';
COMMENT ON COLUMN dispute_tickets.evidence IS
    'Complaint attachments as a JSON array of {evidence_type, object_key, checksum_sha256, uploaded_by_user_id, description, created_at}. Object keys and SHA-256 checksums only - never file contents, never tokens or secrets. Folded into this table instead of a dispute_evidence table by the minimal-schema decision.';
COMMENT ON COLUMN dispute_tickets.penalty_amount IS
    'Optional contractually or lawfully grounded amount recorded with the outcome; not automatically imposed, never deducted or withheld by the platform.';

CREATE INDEX ix_dispute_tickets_order ON dispute_tickets (order_id, created_at DESC);
CREATE INDEX ix_dispute_tickets_status ON dispute_tickets (status, created_at DESC);
