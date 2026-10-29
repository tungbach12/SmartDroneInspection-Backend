-- =============================================================================
-- V8: Re-inspection Source Linkage & Defect Verification
-- =============================================================================

-- 1. Add re-inspection tracking columns to inspections
ALTER TABLE inspections
    ADD COLUMN inspection_type VARCHAR(32) NOT NULL DEFAULT 'INITIAL',
    ADD COLUMN work_order_id UUID REFERENCES maintenance_work_orders(id) ON DELETE SET NULL,
    ADD COLUMN source_inspection_id UUID REFERENCES inspections(id) ON DELETE SET NULL,
    ADD COLUMN source_report_version_id UUID REFERENCES inspection_reports(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_inspections_type CHECK (inspection_type IN ('INITIAL', 'RE_INSPECTION'));

CREATE INDEX ix_inspections_work_order ON inspections (work_order_id) WHERE work_order_id IS NOT NULL;
CREATE INDEX ix_inspections_source_inspection ON inspections (source_inspection_id) WHERE source_inspection_id IS NOT NULL;

-- 2. Add re-inspection resolution tracking to maintenance_work_orders
ALTER TABLE maintenance_work_orders
    ADD COLUMN reinspection_id UUID REFERENCES inspections(id) ON DELETE SET NULL,
    ADD COLUMN reinspection_report_version_id UUID REFERENCES inspection_reports(id) ON DELETE SET NULL;

CREATE INDEX ix_work_orders_reinspection ON maintenance_work_orders (reinspection_id) WHERE reinspection_id IS NOT NULL;

-- 3. Defect verification table for comparing original findings against new re-inspection evidence
CREATE TABLE reinspection_defect_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reinspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    source_finding_id UUID NOT NULL REFERENCES findings(id) ON DELETE RESTRICT,
    new_evidence_id UUID REFERENCES evidence(id) ON DELETE SET NULL,
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    inspector_notes VARCHAR(4000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reinspection_resolution_status CHECK (resolution_status IN ('RESOLVED', 'UNRESOLVED', 'INCONCLUSIVE')),
    CONSTRAINT uq_reinspection_defect UNIQUE (reinspection_id, source_finding_id)
);

CREATE INDEX ix_reinspection_verif_reinspection ON reinspection_defect_verifications (reinspection_id);
CREATE INDEX ix_reinspection_verif_source_finding ON reinspection_defect_verifications (source_finding_id);
