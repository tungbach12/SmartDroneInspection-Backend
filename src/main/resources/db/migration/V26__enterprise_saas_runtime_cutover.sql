-- V26: retire marketplace/MF5 runtime tables after the additive V25 target foundation.
-- V1-V23 stay immutable. Existing legacy data is not assigned invented workflow meaning.
-- This migration is table-scoped; it never uses DROP ... CASCADE.

-- Composite target keys let PostgreSQL reject organization mismatches across entity references.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_id_organization ON users (id, organization_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_assets_id_organization ON assets (id, organization_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_drones_id_organization ON drones (id, organization_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inspections_id_organization ON inspections (id, organization_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inspection_schedules_id_asset ON inspection_schedules (id, asset_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_pair_assignments_id_scope
    ON asset_pair_assignments (id, organization_id, asset_id);

-- Fail closed if existing pair rows cross organization boundaries. This exact relation check is
-- the negation of the three composite foreign keys added below.
DO $$
DECLARE
    offenders TEXT;
BEGIN
    SELECT string_agg(label, '; ' ORDER BY label)
      INTO offenders
      FROM (
          SELECT 'asset_pair_assignments=' || pair.id AS label
            FROM asset_pair_assignments pair
            LEFT JOIN assets asset
              ON asset.id = pair.asset_id AND asset.organization_id = pair.organization_id
            LEFT JOIN users inspector
              ON inspector.id = pair.inspector_user_id AND inspector.organization_id = pair.organization_id
            LEFT JOIN drones drone
              ON drone.id = pair.drone_id AND drone.organization_id = pair.organization_id
           WHERE asset.id IS NULL OR inspector.id IS NULL OR drone.id IS NULL
           ORDER BY pair.id LIMIT 20
      ) invalid_scope;

    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION
            'V26 precheck failed: asset-pair rows cross tenant scope. Repair the listed rows before retrying: %',
            offenders;
    END IF;
END $$;

ALTER TABLE asset_pair_assignments
    ADD CONSTRAINT fk_asset_pair_assignments_asset_tenant
        FOREIGN KEY (asset_id, organization_id) REFERENCES assets (id, organization_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_asset_pair_assignments_inspector_tenant
        FOREIGN KEY (inspector_user_id, organization_id) REFERENCES users (id, organization_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_asset_pair_assignments_drone_tenant
        FOREIGN KEY (drone_id, organization_id) REFERENCES drones (id, organization_id) ON DELETE RESTRICT;

-- V25 copied the old identity into canonical organization fields.
UPDATE organizations SET timezone = 'Asia/Ho_Chi_Minh' WHERE timezone IS NULL;
ALTER TABLE organizations
    ALTER COLUMN legal_name SET NOT NULL,
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN registration_code SET NOT NULL,
    ALTER COLUMN timezone SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;
ALTER TABLE organizations
    DROP COLUMN IF EXISTS name,
    DROP COLUMN IF EXISTS code,
    DROP COLUMN IF EXISTS description,
    DROP COLUMN IF EXISTS active;

-- Asset creation is tenant CRUD, not a review workflow. Translate old review-only statuses before
-- restricting values to the target catalog vocabulary.
ALTER TABLE assets DROP CONSTRAINT IF EXISTS ck_assets_status;
UPDATE assets SET status = 'ACTIVE' WHERE status = 'PENDING_REVIEW';
UPDATE assets SET status = 'INACTIVE' WHERE status = 'REJECTED';
ALTER TABLE assets
    ADD CONSTRAINT ck_assets_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RETIRED'));
ALTER TABLE assets
    DROP CONSTRAINT IF EXISTS uq_assets_organization_code,
    DROP CONSTRAINT IF EXISTS ck_assets_code_normalized;
ALTER TABLE assets ALTER COLUMN asset_code SET NOT NULL;
ALTER TABLE assets
    DROP COLUMN IF EXISTS code,
    DROP COLUMN IF EXISTS location_text,
    DROP COLUMN IF EXISTS ownership_information,
    DROP COLUMN IF EXISTS created_by_user_id;

-- Cadences remain a storage contract; proposal/suggestion execution is retired.
DROP TABLE IF EXISTS schedule_proposals;
DROP TABLE IF EXISTS category_frequency_suggestions;
ALTER TABLE inspection_schedules
    DROP CONSTRAINT IF EXISTS ck_inspection_schedules_frequency_unit,
    DROP CONSTRAINT IF EXISTS ck_inspection_schedules_frequency_interval;
ALTER TABLE inspection_schedules
    DROP COLUMN IF EXISTS checklist_template_id,
    DROP COLUMN IF EXISTS frequency_unit,
    DROP COLUMN IF EXISTS frequency_interval,
    DROP COLUMN IF EXISTS last_generated_due_cycle,
    DROP COLUMN IF EXISTS created_by_user_id;
ALTER TABLE inspection_schedules
    ALTER COLUMN cadence_unit SET NOT NULL,
    ALTER COLUMN cadence_interval SET NOT NULL;
ALTER TABLE inspection_schedules
    ADD CONSTRAINT ck_inspection_schedules_cadence_unit CHECK (cadence_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    ADD CONSTRAINT ck_inspection_schedules_cadence_interval CHECK (cadence_interval > 0);

-- The data cutover removes foreign keys touching the explicit legacy table set only. Unexpected
-- dependencies still fail the migration, rather than being swept away with CASCADE.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT source_ns.nspname AS source_schema,
               source_table.relname AS source_table,
               constraint_row.conname AS constraint_name
          FROM pg_constraint constraint_row
          JOIN pg_class source_table ON source_table.oid = constraint_row.conrelid
          JOIN pg_namespace source_ns ON source_ns.oid = source_table.relnamespace
          JOIN pg_class referenced_table ON referenced_table.oid = constraint_row.confrelid
          JOIN pg_namespace referenced_ns ON referenced_ns.oid = referenced_table.relnamespace
         WHERE constraint_row.contype = 'f'
           AND source_ns.nspname = current_schema()
           AND referenced_ns.nspname = current_schema()
           AND (source_table.relname IN (
                'inspection_requests', 'inspection_request_attachments', 'inspection_quotations',
                'inspection_service_orders', 'inspection_assignments', 'provider_organizations',
                'provider_capabilities', 'provider_vetting_decisions', 'provider_capability_evidence',
                'platform_configurations', 'drone_mission_plans', 'mission_shot_items',
                'security_audit_events', 'maintenance_tickets', 'maintenance_ticket_findings',
                'maintenance_assessments', 'maintenance_quotations', 'maintenance_orders',
                'maintenance_assignments', 'maintenance_change_requests', 'invoices',
                'report_versions', 'dispute_tickets'
            ) OR referenced_table.relname IN (
                'inspection_requests', 'inspection_request_attachments', 'inspection_quotations',
                'inspection_service_orders', 'inspection_assignments', 'provider_organizations',
                'provider_capabilities', 'provider_vetting_decisions', 'provider_capability_evidence',
                'platform_configurations', 'drone_mission_plans', 'mission_shot_items',
                'security_audit_events', 'maintenance_tickets', 'maintenance_ticket_findings',
                'maintenance_assessments', 'maintenance_quotations', 'maintenance_orders',
                'maintenance_assignments', 'maintenance_change_requests', 'invoices',
                'report_versions', 'dispute_tickets'
            ))
    LOOP
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I',
                       fk.source_schema, fk.source_table, fk.constraint_name);
    END LOOP;
END $$;

-- Removing old inspection fields no longer breaks the retired request graph.
ALTER TABLE inspections
    DROP CONSTRAINT IF EXISTS uq_inspections_service_order,
    DROP CONSTRAINT IF EXISTS uq_inspections_assignment,
    DROP CONSTRAINT IF EXISTS ck_inspections_completed,
    DROP CONSTRAINT IF EXISTS ck_inspections_time_order,
    DROP CONSTRAINT IF EXISTS ck_inspections_status;
DROP INDEX IF EXISTS ix_inspections_author_status;
DROP INDEX IF EXISTS ix_inspections_asset_status;

-- Old maintenance work logs point to marketplace tickets/assignments and cannot be deterministically
-- transformed to target work-order/task records. Remove only their obsolete evidence and rows.
DELETE FROM evidence WHERE maintenance_work_log_id IS NOT NULL;
DELETE FROM maintenance_work_logs;
ALTER TABLE evidence DROP CONSTRAINT IF EXISTS ck_evidence_target_parent;
ALTER TABLE evidence
    ADD CONSTRAINT ck_evidence_target_parent CHECK (
        inspection_id IS NOT NULL
        OR field_session_id IS NOT NULL
        OR maintenance_work_order_id IS NOT NULL
        OR maintenance_task_id IS NOT NULL
    );

-- Carry historical security audit rows into the target audit log BEFORE the legacy table is
-- dropped. SRS 3.1.4 requires consequential transitions to stay attributable, and security
-- history (login success/failure, role changes, password resets) is compliance evidence that
-- cannot be reconstructed after the cutover.
--
-- Column mapping: event_type -> action, outcome -> after_status, occurred_at -> created_at.
-- The legacy IP address and user agent have no target column, so they are preserved as safe
-- metadata rather than discarded. aggregate_type is 'USER' because the legacy table could only
-- reference a subject user. This is a data copy, not a reinterpretation of the recorded events.
INSERT INTO audit_events
    (organization_id, actor_user_id, action, aggregate_type, aggregate_id,
     after_status, trace_id, safe_metadata, created_at)
SELECT
    subject.organization_id,
    legacy.actor_user_id,
    legacy.event_type,
    'USER',
    COALESCE(legacy.subject_user_id, legacy.actor_user_id),
    legacy.outcome,
    legacy.correlation_id,
    jsonb_strip_nulls(
        jsonb_build_object(
            'ip_address', NULLIF(legacy.ip_address, ''),
            'user_agent', NULLIF(legacy.user_agent, ''),
            'legacy_table', 'security_audit_events'
        )
    ),
    legacy.occurred_at
FROM security_audit_events legacy
LEFT JOIN users subject ON subject.id = legacy.subject_user_id;

-- Retire every non-target marketplace, old-client, and MF5 application table explicitly.
DROP TABLE IF EXISTS inspection_request_attachments;
DROP TABLE IF EXISTS inspection_assignments;
DROP TABLE IF EXISTS inspection_quotations;
DROP TABLE IF EXISTS drone_mission_plans;
DROP TABLE IF EXISTS mission_shot_items;
DROP TABLE IF EXISTS inspection_service_orders;
DROP TABLE IF EXISTS inspection_requests;
DROP TABLE IF EXISTS provider_capability_evidence;
DROP TABLE IF EXISTS provider_vetting_decisions;
DROP TABLE IF EXISTS provider_capabilities;
DROP TABLE IF EXISTS provider_organizations;
DROP TABLE IF EXISTS platform_configurations;
DROP TABLE IF EXISTS dispute_tickets;
DROP TABLE IF EXISTS security_audit_events;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS maintenance_change_requests;
DROP TABLE IF EXISTS maintenance_ticket_findings;
DROP TABLE IF EXISTS maintenance_assessments;
DROP TABLE IF EXISTS maintenance_quotations;
DROP TABLE IF EXISTS maintenance_assignments;
DROP TABLE IF EXISTS maintenance_orders;
DROP TABLE IF EXISTS maintenance_tickets;
DROP TABLE IF EXISTS report_versions;

-- Inspection rows get organization only from their already-required asset relationship. Map old
-- workflow status vocabulary to storage statuses; this implements no workflow or state transition.
ALTER TABLE inspections
    ADD COLUMN IF NOT EXISTS organization_id UUID,
    ADD COLUMN IF NOT EXISTS schedule_id UUID,
    ADD COLUMN IF NOT EXISTS due_cycle_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS objective VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS scope JSONB,
    ADD COLUMN IF NOT EXISTS component_scope JSONB,
    ADD COLUMN IF NOT EXISTS acceptance_criteria JSONB,
    ADD COLUMN IF NOT EXISTS planned_start_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS planned_end_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS asset_pair_assignment_id UUID,
    ADD COLUMN IF NOT EXISTS inspector_id UUID,
    ADD COLUMN IF NOT EXISTS drone_id UUID;
UPDATE inspections AS inspection
   SET organization_id = asset.organization_id,
       status = CASE inspection.status
           WHEN 'READY_FOR_INSPECTION' THEN 'ASSIGNED'
           WHEN 'AWAITING_AI_REVIEW' THEN 'FIELD_COMPLETED'
           WHEN 'AWAITING_REPORT' THEN 'REPORT_DRAFT'
           ELSE inspection.status
       END
  FROM assets AS asset
 WHERE asset.id = inspection.asset_id;
ALTER TABLE inspections
    ALTER COLUMN organization_id SET NOT NULL,
    DROP COLUMN IF EXISTS service_order_id,
    DROP COLUMN IF EXISTS accepted_assignment_id,
    DROP COLUMN IF EXISTS author_user_id,
    DROP COLUMN IF EXISTS checklist_template_id,
    DROP COLUMN IF EXISTS started_at,
    DROP COLUMN IF EXISTS completed_at;
ALTER TABLE inspections
    ADD CONSTRAINT ck_inspections_status CHECK (status IN (
        'DRAFT', 'ASSIGNED', 'PREPARING', 'READY_FOR_FLIGHT', 'IN_PROGRESS',
        'FIELD_COMPLETED', 'REPORT_DRAFT', 'REPORT_PUBLISHED', 'REPAIR_PENDING', 'COMPLETED', 'CANCELLED'
    )),
    ADD CONSTRAINT fk_inspections_asset_tenant
        FOREIGN KEY (asset_id, organization_id) REFERENCES assets (id, organization_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_inspections_schedule_asset
        FOREIGN KEY (schedule_id, asset_id) REFERENCES inspection_schedules (id, asset_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_inspections_pair_tenant
        FOREIGN KEY (asset_pair_assignment_id, organization_id, asset_id)
        REFERENCES asset_pair_assignments (id, organization_id, asset_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_inspections_inspector_tenant
        FOREIGN KEY (inspector_id, organization_id) REFERENCES users (id, organization_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_inspections_drone_tenant
        FOREIGN KEY (drone_id, organization_id) REFERENCES drones (id, organization_id) ON DELETE RESTRICT;

-- Preserve the target maintenance_work_logs table with V25 target columns, replacing only the old
-- column shape after deleting its unmappable legacy records.
ALTER TABLE maintenance_work_logs
    DROP COLUMN IF EXISTS maintenance_ticket_id,
    DROP COLUMN IF EXISTS execution_assignment_id,
    DROP COLUMN IF EXISTS progress_percent,
    DROP COLUMN IF EXISTS work_summary,
    DROP COLUMN IF EXISTS materials_used,
    DROP COLUMN IF EXISTS labor_hours,
    DROP COLUMN IF EXISTS actual_cost,
    DROP COLUMN IF EXISTS currency,
    DROP COLUMN IF EXISTS submitted_at,
    DROP COLUMN IF EXISTS verified_by_user_id,
    DROP COLUMN IF EXISTS verified_at,
    DROP COLUMN IF EXISTS before_evidence_id,
    DROP COLUMN IF EXISTS after_evidence_id;
ALTER TABLE maintenance_work_logs
    ALTER COLUMN work_order_id SET NOT NULL,
    ALTER COLUMN task_id SET NOT NULL,
    ALTER COLUMN engineer_user_id SET NOT NULL,
    ALTER COLUMN hours SET NOT NULL;

ALTER TABLE maintenance_work_orders
    ADD CONSTRAINT ck_maintenance_work_orders_status CHECK (status IN (
        'DRAFT', 'AWAITING_APPROVAL', 'APPROVED', 'READY', 'IN_PROGRESS', 'WORK_COMPLETED',
        'SUBMITTED_FOR_ACCEPTANCE', 'ACCEPTED', 'COST_RECONCILED', 'CLOSED',
        'REWORK_REQUIRED', 'REINSPECTION_REQUIRED'
    )),
    ADD CONSTRAINT fk_maintenance_work_orders_asset_tenant
        FOREIGN KEY (asset_id, organization_id) REFERENCES assets (id, organization_id) ON DELETE RESTRICT;

COMMENT ON TABLE inspection_preparations IS 'Storage contract only; MF1-MF4 workflow execution is outside this cutover.';
COMMENT ON TABLE maintenance_work_orders IS 'Storage contract only; MF1-MF4 workflow execution is outside this cutover.';
