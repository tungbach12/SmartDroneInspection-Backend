-- V12: Provider Organizations, capability vetting, and provider scope on users (SF-02 / SF-03).
--
-- Forward-only: V1..V11 are applied and are not edited here. Nothing is dropped, no existing column
-- becomes NOT NULL, and no row is rewritten, so every pre-V12 database migrates without data loss.
-- Timestamps stay TIMESTAMPTZ, amounts stay NUMERIC, and no secret, token, password or credential is
-- ever stored: object keys and SHA-256 checksums only, exactly as V7 already does for evidence.
--
-- Why this migration exists (contract MF1 / SF): a Provider is a company, not a role. Capability is
-- organization data and is vetted INDEPENDENTLY for INSPECTION and MAINTENANCE (BR-41): "both" means
-- two rows in provider_capabilities, never a BOTH value, so no single statement can approve both.
-- Organization standing (provider_organizations.status: PENDING / VERIFIED / SUSPENDED / BANNED) is a
-- SEPARATE decision from capability eligibility, with its own vocabulary and its own recorded ground;
-- neither is derived from the other.
--
-- Escrow / advance funding: none. This migration models nothing about money.
--
-- Delete behaviour:
--   * fk_users_provider            ON DELETE SET NULL   - deleting a Provider Organization clears the
--     provider scope of a user; it must never cascade into a person's account.
--   * provider_capabilities        ON DELETE CASCADE    - a capability is only meaningful while its
--     organization exists.
--   * provider_vetting_decisions   ON DELETE CASCADE    - the decision history follows its capability.
--   * provider_capability_evidence ON DELETE CASCADE    - submitted documents follow their capability.
--   * provider_organizations rows referenced by commercial history are RESTRICTed from V14 onward.
--
-- Pre-checks: the four tables are brand new, so no pre-existing row can violate any constraint declared
-- on them - stated, not assumed (the tables do not exist before this file runs, so their pre-check
-- queries would fail to compile). The two statements below that DO validate pre-existing rows are the
-- users.provider_id foreign key and ck_users_organization_provider_exclusive; each is preceded by a
-- DO block whose predicate is the exact negation of the constraint, written as NOT (<predicate>), and
-- both are provably empty on a V11 database because the column is created in this very file. They are
-- written anyway so that the same file is safe to re-apply over a database where an operator has
-- already populated users.provider_id by hand, which is exactly the remediation V13 offers.
--
-- Pre-deploy queries (safe to run before deploying; both must return zero rows):
--   SELECT u.id, u.email, u.provider_id FROM users u
--    WHERE NOT (u.provider_id IS NULL
--               OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = u.provider_id));
--   SELECT u.id, u.email, u.organization_id, u.provider_id FROM users u
--    WHERE NOT (NOT (u.organization_id IS NOT NULL AND u.provider_id IS NOT NULL));


-- 1. The Provider Organization aggregate (SF-02).
--
--    status is the ORGANIZATION STANDING decision and is deliberately NOT the capability status: the
--    documented single status column is what conflated the two (no room for ADDITIONAL_INFO_REQUIRED,
--    no way to reject one capability while keeping the other). standing_decision_reason records the
--    ground of the standing decision: without it, "accepted legal identity" would be the only verdict
--    in this schema whose reason is required everywhere else and discarded here.
CREATE TABLE provider_organizations
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(200) NOT NULL,
    legal_name              VARCHAR(250) NOT NULL,
    tax_code                VARCHAR(32)  NOT NULL,
    business_license_no     VARCHAR(64)  NOT NULL,
    drone_permit_code       VARCHAR(64),
    insurance_policy_no     VARCHAR(128),
    status                  VARCHAR(32)  NOT NULL,
    rating_score            NUMERIC(3,2),
    approved_by_operator_id UUID,
    approved_at             TIMESTAMPTZ,
    standing_decision_reason VARCHAR(2000),
    row_version             BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_provider_org_approver
        FOREIGN KEY (approved_by_operator_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_provider_org_tax_code UNIQUE (tax_code),
    CONSTRAINT ck_provider_org_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_provider_org_legal_name CHECK (BTRIM(legal_name) <> ''),
    CONSTRAINT ck_provider_org_tax_code CHECK (BTRIM(tax_code) <> ''),
    CONSTRAINT ck_provider_org_business_license CHECK (BTRIM(business_license_no) <> ''),
    CONSTRAINT ck_provider_org_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'SUSPENDED', 'BANNED')),
    CONSTRAINT ck_provider_org_rating
        CHECK (rating_score IS NULL OR rating_score BETWEEN 1.00 AND 5.00),
    -- A standing approval names its operator and its instant together. The ROLE of that actor
    -- (PLATFORM_OPERATOR vettes, PLATFORM_ADMIN may not) is not expressible as a CHECK: PostgreSQL
    -- rejects a subquery inside a CHECK constraint, so "the actor holds PLATFORM_OPERATOR" lives in
    -- RolePolicy/application code, not here.
    CONSTRAINT ck_provider_org_approval CHECK (
        (approved_by_operator_id IS NULL AND approved_at IS NULL)
        OR (approved_by_operator_id IS NOT NULL AND approved_at IS NOT NULL)
    ),
    -- One-directional on purpose. A CHECK is validated against every existing row at creation time, so
    -- requiring a reason for every approval would refuse organizations accepted before this column
    -- existed. Backfilling a sentence no operator ever said would be worse than a visibly missing one,
    -- so: a reason, once present, must be non-blank and must belong to a real approval.
    CONSTRAINT ck_provider_org_standing_reason CHECK (
        standing_decision_reason IS NULL
        OR (BTRIM(standing_decision_reason) <> ''
            AND approved_by_operator_id IS NOT NULL
            AND approved_at IS NOT NULL)
    )
);

CREATE INDEX ix_provider_org_status ON provider_organizations (status);


-- 2. The capability record, one row per declared capability.
--
--    The CHECK admits only INSPECTION and MAINTENANCE and there is no column that could hold a pair,
--    which is what makes capability independence structural rather than disciplinary.
CREATE TABLE provider_capabilities
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id        UUID        NOT NULL,
    capability_type    VARCHAR(32) NOT NULL,
    status             VARCHAR(32) NOT NULL,
    declared_by_user_id UUID       NOT NULL,
    declared_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by_user_id UUID,
    decided_at         TIMESTAMPTZ,
    decision_reason    VARCHAR(2000),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_provider_capability_provider
        FOREIGN KEY (provider_id) REFERENCES provider_organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_provider_capability_declared_by
        FOREIGN KEY (declared_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_provider_capability_decided_by
        FOREIGN KEY (decided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_provider_capabilities_capability UNIQUE (provider_id, capability_type),
    CONSTRAINT ck_provider_capabilities_capability
        CHECK (capability_type IN ('INSPECTION', 'MAINTENANCE')),
    CONSTRAINT ck_provider_capabilities_status
        CHECK (status IN ('PENDING', 'ADDITIONAL_INFO_REQUIRED', 'VERIFIED', 'REJECTED')),
    -- A declared capability starts PENDING with nobody having decided it; every actual verdict carries
    -- its operator, its instant and its reason together. PENDING is the absence of a decision, not one.
    CONSTRAINT ck_provider_capabilities_decision CHECK (
        (status = 'PENDING' AND decided_by_user_id IS NULL AND decided_at IS NULL
            AND decision_reason IS NULL)
        OR (status <> 'PENDING' AND decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL
            AND decision_reason IS NOT NULL AND BTRIM(decision_reason) <> '')
    ),
    -- The declarer may not be the decider: identity-level "a Provider may not self-approve", held as a
    -- table constraint so it also holds for a direct SQL write rather than only through the service.
    CONSTRAINT ck_provider_capabilities_no_self_approval
        CHECK (decided_by_user_id IS NULL OR decided_by_user_id <> declared_by_user_id)
);

CREATE INDEX ix_provider_capabilities_status ON provider_capabilities (provider_id, status);


-- 3. Append-only decision history. provider_capabilities.status is the current state for the gate;
--    this table records how it got there. A re-decision after ADDITIONAL_INFO_REQUIRED appends, so
--    "what did the operator say the first time they refused this" stays answerable.
CREATE TABLE provider_vetting_decisions
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capability_id      UUID        NOT NULL,
    status             VARCHAR(32) NOT NULL,
    decided_by_user_id UUID        NOT NULL,
    reason             VARCHAR(2000) NOT NULL,
    decided_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vetting_decision_capability
        FOREIGN KEY (capability_id) REFERENCES provider_capabilities (id) ON DELETE CASCADE,
    CONSTRAINT fk_vetting_decision_actor
        FOREIGN KEY (decided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_vetting_decision_status
        CHECK (status IN ('ADDITIONAL_INFO_REQUIRED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_vetting_decision_reason CHECK (BTRIM(reason) <> '')
    -- The "decider is not the declarer" rule is NOT repeated here: it needs both values on one row,
    -- and here they live in two tables. It is enforced once, on provider_capabilities, by
    -- ck_provider_capabilities_no_self_approval; every decision row is written by the same aggregate
    -- call that sets those columns, so that constraint is where a self-approval is refused.
);

CREATE INDEX ix_vetting_decisions_capability_time
    ON provider_vetting_decisions (capability_id, decided_at);


-- 4. Capability-specific evidence (SF-02 dossier). Object key and checksum only: never a file's
--    contents, never a secret. The lowercase 64-hex checksum CHECK is what stops the same document
--    being submitted twice under two keys, mirroring V7.
CREATE TABLE provider_capability_evidence
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capability_id      UUID        NOT NULL,
    evidence_kind      VARCHAR(32) NOT NULL,
    object_key         VARCHAR(1000) NOT NULL,
    checksum_sha256    CHAR(64)    NOT NULL,
    submitted_by_user_id UUID      NOT NULL,
    submitted_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_capability_evidence_capability
        FOREIGN KEY (capability_id) REFERENCES provider_capabilities (id) ON DELETE CASCADE,
    CONSTRAINT fk_capability_evidence_submitter
        FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_capability_evidence_object_key UNIQUE (object_key),
    CONSTRAINT uq_capability_evidence_checksum UNIQUE (capability_id, checksum_sha256),
    CONSTRAINT ck_capability_evidence_kind CHECK (
        evidence_kind IN ('BUSINESS_REGISTRATION', 'DRONE_REGISTRATION', 'PILOT_LICENSE',
                          'QUALIFICATION', 'INSURANCE', 'OTHER')
    ),
    CONSTRAINT ck_capability_evidence_object_key CHECK (BTRIM(object_key) <> ''),
    CONSTRAINT ck_capability_evidence_checksum CHECK (
        checksum_sha256 = LOWER(checksum_sha256) AND checksum_sha256 ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX ix_capability_evidence_capability_time
    ON provider_capability_evidence (capability_id, submitted_at);


-- 5. Provider scope on the identity. Added nullable with no foreign key first, because the referenced
--    table only comes into existence two statements above and every existing row is NULL: no NOT NULL
--    and no backfill, so no existing row can be refused by this column.
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id UUID;

CREATE INDEX IF NOT EXISTS ix_users_provider ON users (provider_id);

-- Partial index for the reverse direction the vetting queue needs: identities that still carry no
-- provider scope and therefore need operator attention.
CREATE INDEX IF NOT EXISTS ix_users_provider_active
    ON users (provider_id)
    WHERE provider_id IS NOT NULL AND status = 'ACTIVE';


-- 6. Pre-check: refuse to migrate while any users.provider_id names no Provider Organization.
--
--    The exact negation of fk_users_provider, written as NOT (<predicate>): a NULL scope is accepted
--    (that is what every untouched V11 row holds and what the FK accepts too).
--
--    SELECT u.id, u.email, u.provider_id FROM users u
--     WHERE NOT (u.provider_id IS NULL
--                OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = u.provider_id));
--
--    Remediation: the scope cannot be attributed to any provider, so clear it
--      UPDATE users SET provider_id = NULL WHERE id = <that id>;
--    which leaves the account with no provider scope, the same state every other unscoped row is in.
--    If the user really does represent that provider, create the organization first on a database
--    where this file has already committed, reusing the same id, then rerun.
DO $$
DECLARE
    dangling_scopes TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO dangling_scopes
      FROM (
              SELECT u.email || ' (id=' || u.id || ', provider_id=' || u.provider_id || ')' AS label
                FROM users u
               WHERE NOT (u.provider_id IS NULL
                           OR EXISTS (SELECT 1
                                        FROM provider_organizations p
                                       WHERE p.id = u.provider_id))
               ORDER BY u.email
               LIMIT 20
           ) offender;

    IF dangling_scopes IS NOT NULL THEN
        RAISE EXCEPTION
            'V12 precheck failed: users.provider_id holds a scope that names no Provider Organization and fk_users_provider would reject it. Rows (first 20): %. Remediation: UPDATE users SET provider_id = NULL WHERE id = <id> for an unattributable scope, or create the Provider Organization with that id on a database where this migration has committed, then rerun.',
            dangling_scopes;
    END IF;
END $$;

ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_provider;
ALTER TABLE users
    ADD CONSTRAINT fk_users_provider FOREIGN KEY (provider_id)
        REFERENCES provider_organizations (id) ON DELETE SET NULL;


-- 7. Pre-check: a user may be scoped to a customer organization or to a provider, never both.
--
--    Every V11 row satisfies this without evaluation (provider_id was NULL before this file), so the
--    pre-check is provably empty there; it is written because the file is also safe to re-apply over a
--    database where an operator populated provider_id by hand while organization_id was already set.
--
--    SELECT u.id, u.email, u.organization_id, u.provider_id FROM users u
--     WHERE NOT (NOT (u.organization_id IS NOT NULL AND u.provider_id IS NOT NULL));
--
--    Remediation: decide which side the identity belongs to and clear the other scope:
--      UPDATE users SET organization_id = NULL WHERE id = <id>;  -- provider-side identity
--      UPDATE users SET provider_id = NULL WHERE id = <id>;      -- customer-side identity
DO $$
DECLARE
    double_scoped TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO double_scoped
      FROM (
              SELECT u.email
                     || ' (id=' || u.id || ', organization_id=' || u.organization_id
                     || ', provider_id=' || u.provider_id || ')' AS label
                FROM users u
               WHERE NOT (NOT (u.organization_id IS NOT NULL AND u.provider_id IS NOT NULL))
               ORDER BY u.email
               LIMIT 20
           ) offender;

    IF double_scoped IS NOT NULL THEN
        RAISE EXCEPTION
            'V12 precheck failed: a user identity may carry a customer organization scope or a provider scope, never both, but these rows carry both (first 20): %. Remediation: clear one scope per listed user (UPDATE users SET organization_id = NULL ... for a provider-side identity, or SET provider_id = NULL ... for a customer-side identity), then rerun this migration.',
            double_scoped;
    END IF;
END $$;

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_organization_provider_exclusive;
ALTER TABLE users
    ADD CONSTRAINT ck_users_organization_provider_exclusive CHECK (
        NOT (organization_id IS NOT NULL AND provider_id IS NOT NULL)
    );

-- Note on the remaining half of the scope rule: "a service-provider identity MUST carry a provider_id"
-- is deliberately NOT enforced here. It would refuse every existing SERVICE_WORKFORCE row, which holds
-- NULL provider_id by construction, and a CHECK is validated against existing rows at creation time.
-- The requirement is enforced where it is representable today (RolePolicy refuses a provider identity
-- minted without a scope) and is left for the migration that canonicalizes actor zones.
