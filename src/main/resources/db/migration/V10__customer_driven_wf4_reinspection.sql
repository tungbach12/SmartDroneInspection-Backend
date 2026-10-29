-- Add the customer-driven WF4 lifecycle and reinspection request persistence.
-- The preceding MF1 V7 and MF2 V8-V9 migrations own the role vocabulary and partner assignments.

-- Customer-driven MF4 states and decision fields on work orders.
ALTER TABLE maintenance_work_orders
    ADD COLUMN IF NOT EXISTS customer_decision character varying(32),
    ADD COLUMN IF NOT EXISTS customer_decision_note character varying(4000);

UPDATE maintenance_work_orders
SET status = 'AWAITING_CUSTOMER_ACCEPTANCE'
WHERE status = 'SUBMITTED_FOR_ACCEPTANCE';

UPDATE maintenance_work_orders
SET status = 'AWAITING_REINSPECTION'
WHERE status = 'REINSPECTION_REQUIRED';

ALTER TABLE maintenance_work_orders
    DROP CONSTRAINT ck_maintenance_work_orders_status;
ALTER TABLE maintenance_work_orders
    ADD CONSTRAINT ck_maintenance_work_orders_status CHECK (
        status IN ('DRAFT', 'AWAITING_APPROVAL', 'APPROVED', 'READY', 'IN_PROGRESS',
                   'WORK_COMPLETED', 'SUBMITTED_FOR_ACCEPTANCE', 'ACCEPTED',
                   'COST_RECONCILED', 'CLOSED', 'REWORK_REQUIRED',
                   'REINSPECTION_REQUIRED', 'AWAITING_REINSPECTION', 'REINSPECTION_IN_PROGRESS',
                   'AWAITING_CUSTOMER_ACCEPTANCE', 'REPAIR_SUPPLEMENT_REQUIRED')
    );

ALTER TABLE maintenance_work_orders
    ADD CONSTRAINT ck_maintenance_work_orders_customer_decision CHECK (
        customer_decision IS NULL OR customer_decision IN ('ACCEPTED', 'REPAIR_REQUIRED')
    );

DROP INDEX IF EXISTS uq_maintenance_work_orders_active_finding;
CREATE UNIQUE INDEX uq_maintenance_work_orders_active_finding
    ON maintenance_work_orders (source_finding_id)
    WHERE status IN ('DRAFT', 'AWAITING_APPROVAL', 'APPROVED', 'READY', 'IN_PROGRESS',
                     'WORK_COMPLETED', 'SUBMITTED_FOR_ACCEPTANCE', 'REWORK_REQUIRED',
                     'REINSPECTION_REQUIRED', 'AWAITING_REINSPECTION',
                     'REINSPECTION_IN_PROGRESS', 'AWAITING_CUSTOMER_ACCEPTANCE',
                     'REPAIR_SUPPLEMENT_REQUIRED');

CREATE TABLE maintenance_reinspection_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    work_order_id uuid NOT NULL REFERENCES maintenance_work_orders(id) ON DELETE RESTRICT,
    maintenance_report_version_id uuid NOT NULL REFERENCES maintenance_reports(id) ON DELETE RESTRICT,
    requested_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    finding_ids uuid[] NOT NULL,
    request_note character varying(4000),
    status character varying(32) NOT NULL,
    manager_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    inspector_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    drone_id uuid REFERENCES drones(id) ON DELETE RESTRICT,
    inspection_id uuid UNIQUE REFERENCES inspections(id) ON DELETE RESTRICT,
    inspection_report_id uuid REFERENCES inspection_reports(id) ON DELETE RESTRICT,
    customer_decision character varying(32),
    customer_decision_note character varying(4000),
    requested_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    assigned_at timestamp with time zone,
    decided_at timestamp with time zone,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_maintenance_reinspection_requests_status CHECK (
        status IN ('REQUESTED', 'ASSIGNED', 'INSPECTION_PUBLISHED', 'ACCEPTED', 'RETURNED', 'CANCELLED')
    ),
    CONSTRAINT ck_maintenance_reinspection_requests_customer_decision CHECK (
        customer_decision IS NULL OR customer_decision IN ('ACCEPTED', 'REPAIR_REQUIRED')
    ),
    CONSTRAINT ck_maintenance_reinspection_requests_findings_non_empty CHECK (
        cardinality(finding_ids) >= 1
    ),
    CONSTRAINT uq_maintenance_reinspection_request_report UNIQUE (work_order_id, maintenance_report_version_id)
);

CREATE INDEX ix_maintenance_reinspection_requests_queue
    ON maintenance_reinspection_requests (status, requested_at);
CREATE INDEX ix_maintenance_reinspection_requests_org_work_order
    ON maintenance_reinspection_requests (organization_id, work_order_id, requested_at DESC);
