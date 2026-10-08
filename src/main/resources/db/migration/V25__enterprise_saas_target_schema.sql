-- V25: additive Enterprise SaaS target-schema foundation.
-- Applied V1..V24 and legacy runtime tables/columns are intentionally preserved.
-- This migration adds target records and compatibility columns only. Legacy table
-- retirement is deferred until all runtime entity mappings have been removed.

-- Tenant contract columns are nullable during this additive bridge so existing
-- organizations retain their current JPA mapping and historical rows unchanged.
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS legal_name VARCHAR(250),
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS registration_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

UPDATE organizations
SET legal_name = COALESCE(legal_name, name),
    display_name = COALESCE(display_name, name),
    registration_code = COALESCE(registration_code, code),
    status = COALESCE(status, CASE WHEN active THEN 'ACTIVE' ELSE 'SUSPENDED' END)
WHERE legal_name IS NULL OR display_name IS NULL OR registration_code IS NULL OR status IS NULL;

-- The current Organization entity does not yet write these new target fields;
-- keep them nullable during the runtime-entity cutover, while enforcing uniqueness
-- for populated registration codes.
CREATE UNIQUE INDEX IF NOT EXISTS uq_organizations_registration_code
    ON organizations (registration_code) WHERE registration_code IS NOT NULL;

-- Tenant subscription history; no payment credentials are stored.
CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    plan_code VARCHAR(32) NOT NULL,
    billing_period_months SMALLINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    external_reference VARCHAR(128),
    terms_snapshot JSONB,
    confirmed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_subscriptions_plan CHECK (plan_code = 'ENTERPRISE'),
    CONSTRAINT ck_subscriptions_billing_period CHECK (billing_period_months IN (1, 6, 12)),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('PENDING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_subscriptions_window CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS ix_subscriptions_organization_status ON subscriptions (organization_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_active_organization
    ON subscriptions (organization_id) WHERE status = 'ACTIVE';

-- Workforce qualifications and compliance references.
CREATE TABLE IF NOT EXISTS workforce_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    credential_type VARCHAR(64) NOT NULL,
    issuer VARCHAR(200),
    credential_reference VARCHAR(200),
    issued_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    evidence_id UUID,
    verified_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    verified_at TIMESTAMPTZ,
    verification_reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_workforce_credentials_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'EXPIRING_SOON', 'EXPIRED', 'REJECTED', 'SUSPENDED'))
);
CREATE INDEX IF NOT EXISTS ix_workforce_credentials_org_user_status_expiry
    ON workforce_credentials (organization_id, user_id, status, expires_at);

-- Organization-owned Drone inventory and documents.
CREATE TABLE IF NOT EXISTS drones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    serial_number VARCHAR(128) NOT NULL,
    model VARCHAR(200),
    manufacturer VARCHAR(200),
    payload_metadata JSONB,
    serviceability VARCHAR(24) NOT NULL,
    last_maintenance_at TIMESTAMPTZ,
    next_maintenance_at TIMESTAMPTZ,
    notes VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_drones_organization_serial UNIQUE (organization_id, serial_number),
    CONSTRAINT ck_drones_serviceability CHECK (serviceability IN ('ACTIVE', 'MAINTENANCE', 'SUSPENDED', 'RETIRED'))
);
CREATE INDEX IF NOT EXISTS ix_drones_organization_status ON drones (organization_id, serviceability);

CREATE TABLE IF NOT EXISTS drone_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drone_id UUID NOT NULL REFERENCES drones(id) ON DELETE RESTRICT,
    document_type VARCHAR(48) NOT NULL,
    issuer VARCHAR(200),
    document_reference VARCHAR(200),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    object_key VARCHAR(1000) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_drone_documents_object_key UNIQUE (object_key),
    CONSTRAINT ck_drone_documents_status CHECK (status IN ('PENDING_REVIEW', 'ACTIVE', 'EXPIRED', 'REJECTED', 'REVOKED')),
    CONSTRAINT ck_drone_documents_validity CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from)
);
CREATE INDEX IF NOT EXISTS ix_drone_documents_drone_status_validity ON drone_documents (drone_id, status, valid_until);

CREATE TABLE IF NOT EXISTS flight_permits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    asset_id UUID REFERENCES assets(id) ON DELETE RESTRICT,
    area_reference VARCHAR(200),
    permit_type VARCHAR(64) NOT NULL,
    issuing_authority VARCHAR(200),
    permit_reference VARCHAR(200),
    geographic_scope JSONB,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    conditions JSONB,
    status VARCHAR(24) NOT NULL,
    source_evidence_id UUID REFERENCES evidence(id) ON DELETE RESTRICT,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    review_reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_flight_permits_status CHECK (status IN ('APPLICATION', 'ACTIVE', 'EXPIRED', 'REJECTED', 'REVOKED', 'NOT_APPLICABLE')),
    CONSTRAINT ck_flight_permits_validity CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from)
);
CREATE INDEX IF NOT EXISTS ix_flight_permits_org_status_validity ON flight_permits (organization_id, status, valid_from, valid_until);

-- Extend existing catalog tables without changing legacy JPA columns.
ALTER TABLE asset_categories
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE checklist_templates
    ADD COLUMN IF NOT EXISTS context VARCHAR(32),
    ADD COLUMN IF NOT EXISTS active_from TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS active_until TIMESTAMPTZ;
ALTER TABLE checklist_items
    ADD COLUMN IF NOT EXISTS evidence_requirement JSONB;

ALTER TABLE assets
    ADD COLUMN IF NOT EXISTS asset_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS asset_type VARCHAR(96),
    ADD COLUMN IF NOT EXISTS location JSONB,
    ADD COLUMN IF NOT EXISTS technical_profile JSONB;
UPDATE assets SET asset_code = code WHERE asset_code IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_assets_organization_asset_code
    ON assets (organization_id, asset_code) WHERE asset_code IS NOT NULL;
ALTER TABLE asset_documents
    ADD COLUMN IF NOT EXISTS document_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS asset_pair_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
    inspector_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    drone_id UUID NOT NULL REFERENCES drones(id) ON DELETE RESTRICT,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(2000),
    assigned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_asset_pair_assignments_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'SUSPENDED')),
    CONSTRAINT ck_asset_pair_assignments_window CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE INDEX IF NOT EXISTS ix_asset_pair_assignments_asset_status_from
    ON asset_pair_assignments (asset_id, status, valid_from);
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_pair_assignments_active_asset
    ON asset_pair_assignments (asset_id) WHERE status = 'ACTIVE';

ALTER TABLE inspection_schedules
    ADD COLUMN IF NOT EXISTS cadence_unit VARCHAR(16),
    ADD COLUMN IF NOT EXISTS cadence_interval INTEGER,
    ADD COLUMN IF NOT EXISTS scope_defaults JSONB;
UPDATE inspection_schedules
SET cadence_unit = COALESCE(cadence_unit, frequency_unit),
    cadence_interval = COALESCE(cadence_interval, frequency_interval)
WHERE cadence_unit IS NULL OR cadence_interval IS NULL;

-- Keep old runtime inspection FKs/columns; the new snapshot fields are additive.
ALTER TABLE inspections
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS schedule_id UUID REFERENCES inspection_schedules(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS due_cycle_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS objective VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS scope JSONB,
    ADD COLUMN IF NOT EXISTS component_scope JSONB,
    ADD COLUMN IF NOT EXISTS acceptance_criteria JSONB,
    ADD COLUMN IF NOT EXISTS planned_start_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS planned_end_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS asset_pair_assignment_id UUID REFERENCES asset_pair_assignments(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS inspector_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS drone_id UUID REFERENCES drones(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS ix_inspections_org_asset_status_planned
    ON inspections (organization_id, asset_id, status, planned_start_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inspections_schedule_due_cycle
    ON inspections (schedule_id, due_cycle_key)
    WHERE schedule_id IS NOT NULL AND due_cycle_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS inspection_preparations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE RESTRICT,
    inspector_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    preparation_version INTEGER NOT NULL,
    shot_list JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_types JSONB,
    access_constraints JSONB,
    safety_observations VARCHAR(4000),
    permit_document_references JSONB,
    submitted_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspection_preparations_version UNIQUE (inspection_id, preparation_version),
    CONSTRAINT ck_inspection_preparations_version CHECK (preparation_version > 0),
    CONSTRAINT ck_inspection_preparations_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'READY'))
);
CREATE INDEX IF NOT EXISTS ix_inspection_preparations_inspection_status ON inspection_preparations (inspection_id, status);

CREATE TABLE IF NOT EXISTS inspection_readiness_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE RESTRICT,
    preparation_id UUID REFERENCES inspection_preparations(id) ON DELETE RESTRICT,
    preparation_version INTEGER,
    decision VARCHAR(24) NOT NULL,
    reviewed_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(2000),
    permit_snapshot JSONB,
    credential_snapshot JSONB,
    drone_document_snapshot JSONB,
    permit_snapshot_ids JSONB,
    credential_snapshot_ids JSONB,
    drone_document_snapshot_ids JSONB,
    source_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inspection_readiness_decision CHECK (decision IN ('APPROVED', 'RETURNED', 'INVALIDATED'))
);
CREATE INDEX IF NOT EXISTS ix_readiness_decisions_inspection_time ON inspection_readiness_decisions (inspection_id, decided_at DESC);

CREATE TABLE IF NOT EXISTS field_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE RESTRICT,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    inspector_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    drone_id UUID REFERENCES drones(id) ON DELETE RESTRICT,
    readiness_decision_id UUID REFERENCES inspection_readiness_decisions(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    postponement_reason VARCHAR(2000),
    abort_reason VARCHAR(2000),
    checklist_template_id UUID REFERENCES checklist_templates(id) ON DELETE RESTRICT,
    checklist_version INTEGER,
    readiness_version INTEGER,
    limitations JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_field_sessions_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'POSTPONED', 'ABORTED')),
    CONSTRAINT ck_field_sessions_time_order CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX IF NOT EXISTS ix_field_sessions_inspection_status_started ON field_sessions (inspection_id, status, started_at);

ALTER TABLE checklist_responses
    ADD COLUMN IF NOT EXISTS context VARCHAR(32),
    ADD COLUMN IF NOT EXISTS template_version INTEGER,
    ADD COLUMN IF NOT EXISTS item_version INTEGER,
    ADD COLUMN IF NOT EXISTS responder_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS answer JSONB,
    ADD COLUMN IF NOT EXISTS evidence_id UUID,
    ADD COLUMN IF NOT EXISTS field_session_id UUID REFERENCES field_sessions(id) ON DELETE RESTRICT;
UPDATE checklist_responses SET answer = response_value WHERE answer IS NULL;

-- Evidence carries metadata only; object bytes remain in MinIO.
ALTER TABLE evidence
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS field_session_id UUID REFERENCES field_sessions(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS maintenance_work_order_id UUID,
    ADD COLUMN IF NOT EXISTS maintenance_task_id UUID,
    ADD COLUMN IF NOT EXISTS kind VARCHAR(32),
    ADD COLUMN IF NOT EXISTS immutable_original BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS capture_metadata JSONB;
UPDATE evidence SET kind = evidence_kind WHERE kind IS NULL;

ALTER TABLE workforce_credentials
    ADD CONSTRAINT fk_workforce_credentials_evidence
    FOREIGN KEY (evidence_id) REFERENCES evidence(id) ON DELETE RESTRICT;
ALTER TABLE flight_permits
    ADD CONSTRAINT fk_flight_permits_evidence
    FOREIGN KEY (source_evidence_id) REFERENCES evidence(id) ON DELETE RESTRICT;
ALTER TABLE checklist_responses
    ADD CONSTRAINT fk_checklist_responses_evidence
    FOREIGN KEY (evidence_id) REFERENCES evidence(id) ON DELETE RESTRICT;
ALTER TABLE flight_permits
    ADD CONSTRAINT fk_flight_permits_source_evidence
    FOREIGN KEY (source_evidence_id) REFERENCES evidence(id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS evidence_quality_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE RESTRICT,
    field_session_id UUID REFERENCES field_sessions(id) ON DELETE RESTRICT,
    decision VARCHAR(32) NOT NULL,
    shot_list_comparison JSONB,
    limitation_reason VARCHAR(2000),
    decided_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_evidence_quality_decision CHECK (decision IN ('PENDING', 'ACCEPTED', 'REUPLOAD_REQUIRED', 'ADDITIONAL_SESSION_REQUIRED', 'LIMITED'))
);
CREATE INDEX IF NOT EXISTS ix_evidence_quality_inspection_time ON evidence_quality_decisions (inspection_id, decided_at DESC);

ALTER TABLE ai_finding_candidates
    ADD COLUMN IF NOT EXISTS inspection_id UUID REFERENCES inspections(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS model_provider VARCHAR(96),
    ADD COLUMN IF NOT EXISTS raw_result_reference VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(24);
UPDATE ai_finding_candidates candidate
SET inspection_id = evidence.inspection_id
FROM evidence
WHERE candidate.evidence_id = evidence.id AND candidate.inspection_id IS NULL;

ALTER TABLE verified_findings
    ADD COLUMN IF NOT EXISTS component VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS observed_condition VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS priority VARCHAR(24),
    ADD COLUMN IF NOT EXISTS measurement JSONB,
    ADD COLUMN IF NOT EXISTS decision VARCHAR(24),
    ADD COLUMN IF NOT EXISTS decided_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS decided_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rationale VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS repair_required BOOLEAN;
UPDATE verified_findings
SET component = COALESCE(component, finding_code),
    description = COALESCE(description, defect_label),
    observed_condition = COALESCE(observed_condition, technical_notes),
    decision = COALESCE(decision, 'CONFIRMED'),
    repair_required = COALESCE(repair_required, status <> 'RESOLVED')
WHERE component IS NULL OR description IS NULL OR observed_condition IS NULL OR decision IS NULL OR repair_required IS NULL;

-- New target names coexist with legacy report_versions until entity retirement.
CREATE TABLE IF NOT EXISTS inspection_report_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_report_id UUID NOT NULL REFERENCES inspection_reports(id) ON DELETE RESTRICT,
    version_no INTEGER NOT NULL,
    source_version_id UUID REFERENCES inspection_report_versions(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    author_verified_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    author_verified_at TIMESTAMPTZ,
    reviewer_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    review_reason VARCHAR(2000),
    llm_provider VARCHAR(96),
    llm_model VARCHAR(160),
    prompt_version VARCHAR(96),
    generated_at TIMESTAMPTZ,
    content_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    rendered_object_key VARCHAR(1000),
    rendered_checksum_sha256 CHAR(64),
    evidence_snapshot_hash VARCHAR(128),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inspection_report_versions_number UNIQUE (inspection_report_id, version_no),
    CONSTRAINT ck_inspection_report_versions_number CHECK (version_no > 0),
    CONSTRAINT ck_inspection_report_versions_status CHECK (status IN ('DRAFT', 'AUTHOR_VERIFIED', 'SUBMITTED', 'RETURNED', 'APPROVED', 'PUBLISHED', 'SUPERSEDED'))
);
CREATE INDEX IF NOT EXISTS ix_inspection_report_versions_report_status ON inspection_report_versions (inspection_report_id, status, version_no DESC);

CREATE TABLE IF NOT EXISTS report_version_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_version_id UUID NOT NULL REFERENCES inspection_report_versions(id) ON DELETE RESTRICT,
    evidence_id UUID NOT NULL REFERENCES evidence(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_report_version_evidence UNIQUE (report_version_id, evidence_id)
);
CREATE INDEX IF NOT EXISTS ix_report_version_evidence_evidence ON report_version_evidence (evidence_id);

CREATE TABLE IF NOT EXISTS report_version_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_version_id UUID NOT NULL REFERENCES inspection_report_versions(id) ON DELETE RESTRICT,
    finding_id UUID NOT NULL REFERENCES verified_findings(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_report_version_findings UNIQUE (report_version_id, finding_id)
);
CREATE INDEX IF NOT EXISTS ix_report_version_findings_finding ON report_version_findings (finding_id);

-- MF4 storage contracts only: these records do not implement workflow transitions.
CREATE TABLE IF NOT EXISTS maintenance_work_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
    source_report_version_id UUID NOT NULL REFERENCES inspection_report_versions(id) ON DELETE RESTRICT,
    source_finding_id UUID NOT NULL REFERENCES verified_findings(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(24),
    due_at TIMESTAMPTZ,
    corrective_scope JSONB,
    acceptance_criteria JSONB,
    owner_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    budget_approver_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    team_lead_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    report_author_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    accepting_reviewer_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_maintenance_work_orders_org_status_due ON maintenance_work_orders (organization_id, status, due_at);
CREATE INDEX IF NOT EXISTS ix_maintenance_work_orders_source_finding_status ON maintenance_work_orders (source_finding_id, status);
ALTER TABLE evidence
    ADD CONSTRAINT fk_evidence_target_work_order
    FOREIGN KEY (maintenance_work_order_id) REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT;

ALTER TABLE evidence DROP CONSTRAINT IF EXISTS ck_evidence_parent;
ALTER TABLE evidence
    ADD CONSTRAINT ck_evidence_target_parent CHECK (
        inspection_id IS NOT NULL
        OR field_session_id IS NOT NULL
        OR maintenance_work_order_id IS NOT NULL
        OR maintenance_task_id IS NOT NULL
        OR maintenance_work_log_id IS NOT NULL
    );

ALTER TABLE maintenance_work_logs
    ADD COLUMN IF NOT EXISTS work_order_id UUID REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS task_id UUID,
    ADD COLUMN IF NOT EXISTS engineer_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS hours NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS actual_cost_references JSONB,
    ADD COLUMN IF NOT EXISTS as_left_condition VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS test_readings JSONB;
UPDATE maintenance_work_logs
SET hours = labor_hours
WHERE hours IS NULL;

CREATE TABLE IF NOT EXISTS maintenance_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    task_number INTEGER NOT NULL,
    name VARCHAR(300) NOT NULL,
    method VARCHAR(4000),
    assigned_engineer_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    planned_start_at TIMESTAMPTZ,
    planned_end_at TIMESTAMPTZ,
    acceptance_criteria JSONB,
    status VARCHAR(24) NOT NULL,
    completion_notes VARCHAR(4000),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_maintenance_tasks_number UNIQUE (work_order_id, task_number),
    CONSTRAINT ck_maintenance_tasks_status CHECK (status IN ('PLANNED', 'READY', 'IN_PROGRESS', 'WORK_COMPLETED', 'REWORK_REQUIRED', 'ACCEPTED', 'CANCELLED')),
    CONSTRAINT ck_maintenance_tasks_order CHECK (task_number > 0 AND display_order >= 0)
);
CREATE INDEX IF NOT EXISTS ix_maintenance_tasks_work_order_status ON maintenance_tasks (work_order_id, status);
ALTER TABLE evidence
    ADD CONSTRAINT fk_evidence_target_task
    FOREIGN KEY (maintenance_task_id) REFERENCES maintenance_tasks(id) ON DELETE RESTRICT;
ALTER TABLE maintenance_work_logs
    ADD CONSTRAINT fk_maintenance_work_logs_target_task
    FOREIGN KEY (task_id) REFERENCES maintenance_tasks(id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS maintenance_team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    engineer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    member_role VARCHAR(24) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    assigned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_maintenance_team_member_role CHECK (member_role IN ('LEAD', 'REPORT_AUTHOR', 'MEMBER')),
    CONSTRAINT ck_maintenance_team_member_window CHECK (effective_until IS NULL OR effective_until > effective_from)
);
CREATE INDEX IF NOT EXISTS ix_maintenance_team_members_work_order_active ON maintenance_team_members (work_order_id, active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_team_lead_active ON maintenance_team_members (work_order_id) WHERE active AND member_role = 'LEAD';
CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_team_report_author_active ON maintenance_team_members (work_order_id) WHERE active AND member_role = 'REPORT_AUTHOR';

CREATE TABLE IF NOT EXISTS maintenance_estimate_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    version_no INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    prepared_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    approved_at TIMESTAMPTZ,
    currency CHAR(3) NOT NULL,
    tax_basis JSONB,
    baseline_total NUMERIC(18,2) NOT NULL DEFAULT 0,
    assumptions JSONB,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_maintenance_estimate_versions_number UNIQUE (work_order_id, version_no),
    CONSTRAINT ck_maintenance_estimate_version_number CHECK (version_no > 0),
    CONSTRAINT ck_maintenance_estimate_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT ck_maintenance_estimate_total CHECK (baseline_total >= 0)
);
CREATE INDEX IF NOT EXISTS ix_maintenance_estimate_versions_status ON maintenance_estimate_versions (work_order_id, status);

CREATE TABLE IF NOT EXISTS maintenance_change_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    change_number INTEGER NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    affected_tasks JSONB,
    proposed_delta NUMERIC(18,2),
    supporting_evidence JSONB,
    proposed_start_at TIMESTAMPTZ,
    proposed_end_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    requested_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decided_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_maintenance_change_orders_number UNIQUE (work_order_id, change_number),
    CONSTRAINT ck_maintenance_change_orders_number CHECK (change_number > 0),
    CONSTRAINT ck_maintenance_change_orders_status CHECK (status IN ('DRAFT', 'AWAITING_APPROVAL', 'APPROVED', 'REJECTED', 'RETURNED', 'SUPERSEDED'))
);
CREATE INDEX IF NOT EXISTS ix_maintenance_change_orders_status ON maintenance_change_orders (work_order_id, status);

CREATE TABLE IF NOT EXISTS maintenance_cost_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    estimate_version_id UUID REFERENCES maintenance_estimate_versions(id) ON DELETE RESTRICT,
    change_order_id UUID REFERENCES maintenance_change_orders(id) ON DELETE RESTRICT,
    task_id UUID REFERENCES maintenance_tasks(id) ON DELETE RESTRICT,
    line_kind VARCHAR(24) NOT NULL,
    state VARCHAR(24) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    quantity NUMERIC(18,6) NOT NULL DEFAULT 1,
    unit VARCHAR(48),
    unit_rate NUMERIC(18,6) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    tax_treatment JSONB,
    evidence_reference VARCHAR(1000),
    entered_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    entered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_maintenance_cost_line_kind CHECK (line_kind IN ('LABOR', 'MATERIAL', 'EQUIPMENT', 'EXTERNAL_SERVICE', 'OTHER', 'CONTINGENCY')),
    CONSTRAINT ck_maintenance_cost_line_state CHECK (state IN ('ESTIMATE', 'CHANGE', 'ACTUAL')),
    CONSTRAINT ck_maintenance_cost_line_values CHECK (quantity >= 0 AND unit_rate >= 0 AND amount >= 0)
);
CREATE INDEX IF NOT EXISTS ix_maintenance_cost_lines_work_order_kind_state ON maintenance_cost_lines (work_order_id, line_kind, state);

CREATE TABLE IF NOT EXISTS maintenance_report_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    author_verified_at TIMESTAMPTZ,
    llm_provider VARCHAR(96),
    llm_model VARCHAR(160),
    prompt_version VARCHAR(96),
    generated_at TIMESTAMPTZ,
    approved_scope_hash VARCHAR(128),
    change_snapshot_hash VARCHAR(128),
    work_log_snapshot_hash VARCHAR(128),
    actual_cost_snapshot_hash VARCHAR(128),
    content_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    rendered_object_key VARCHAR(1000),
    rendered_checksum_sha256 CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_maintenance_report_versions_number UNIQUE (work_order_id, version_no),
    CONSTRAINT ck_maintenance_report_versions_number CHECK (version_no > 0),
    CONSTRAINT ck_maintenance_report_versions_status CHECK (status IN ('DRAFT', 'AUTHOR_VERIFIED', 'SUBMITTED', 'RETURNED', 'APPROVED', 'SUPERSEDED'))
);
CREATE INDEX IF NOT EXISTS ix_maintenance_report_versions_work_order ON maintenance_report_versions (work_order_id, version_no DESC);

CREATE TABLE IF NOT EXISTS maintenance_acceptance_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    report_version_id UUID NOT NULL REFERENCES maintenance_report_versions(id) ON DELETE RESTRICT,
    reviewer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decision VARCHAR(32) NOT NULL,
    technical_comments VARCHAR(4000),
    acceptance_checklist JSONB,
    test_result JSONB,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signature_reference VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_maintenance_acceptance_decision CHECK (decision IN ('ACCEPTED', 'REWORK_REQUIRED', 'REINSPECTION_REQUIRED', 'REJECTED'))
);
CREATE INDEX IF NOT EXISTS ix_maintenance_acceptance_work_order_time ON maintenance_acceptance_decisions (work_order_id, decided_at DESC);

-- Cross-cutting business audit log; existing security_audit_events remains intact.
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    before_status VARCHAR(64),
    after_status VARCHAR(64),
    aggregate_version BIGINT,
    reason VARCHAR(2000),
    trace_id VARCHAR(128),
    safe_metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_audit_events_org_aggregate_time
    ON audit_events (organization_id, aggregate_type, aggregate_id, created_at);

-- Notification target references are optional until producers move to the new aggregates.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS aggregate_id UUID;

-- Persisted vocabulary must match the current entity enums before any new transition is written.
ALTER TABLE schedule_proposals DROP CONSTRAINT IF EXISTS ck_schedule_proposal_status;
UPDATE schedule_proposals SET status = 'ORG_ADMIN_SELECTED' WHERE status = 'CLIENT_SELECTED';
ALTER TABLE schedule_proposals
    ADD CONSTRAINT ck_schedule_proposal_status CHECK (
        status IN ('GENERATED', 'MANAGER_APPROVED', 'MANAGER_REJECTED', 'ORG_ADMIN_SELECTED', 'SUPERSEDED')
    );

ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS ck_maintenance_tickets_status;
UPDATE maintenance_tickets
   SET status = 'AWAITING_ORG_ADMIN_APPROVAL'
 WHERE status = 'AWAITING_CLIENT_APPROVAL';
ALTER TABLE maintenance_tickets
    ADD CONSTRAINT ck_maintenance_tickets_status CHECK (status IN (
        'SUBMITTED', 'ASSESSMENT_PENDING', 'ASSESSED', 'QUOTATION_PENDING',
        'AWAITING_ORG_ADMIN_APPROVAL', 'ORDER_CONFIRMED', 'EXECUTION_PENDING',
        'IN_PROGRESS', 'CHANGE_PENDING', 'INTERNAL_REVIEW', 'RELEASED',
        'REWORK_REQUESTED', 'REINSPECTION_REQUESTED', 'CLOSED', 'CANCELLED'
    ));

-- V25 introduces workflow storage, but it intentionally does not implement workflow execution.
COMMENT ON TABLE inspection_preparations IS
    'Storage foundation only; workflow behavior is owned by the feature implementation phase.';
COMMENT ON TABLE maintenance_work_orders IS
    'Storage foundation only; workflow behavior is owned by the feature implementation phase.';
