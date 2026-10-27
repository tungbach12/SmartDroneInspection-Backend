-- Fail rather than silently deleting existing duplicates; resolve them before retrying migration.
CREATE UNIQUE INDEX uq_maintenance_work_orders_active_finding
    ON maintenance_work_orders (source_finding_id)
    WHERE status IN ('DRAFT', 'AWAITING_APPROVAL', 'APPROVED', 'READY', 'IN_PROGRESS',
                     'WORK_COMPLETED', 'SUBMITTED_FOR_ACCEPTANCE', 'REWORK_REQUIRED',
                     'REINSPECTION_REQUIRED');

CREATE UNIQUE INDEX uq_maintenance_reports_active
    ON maintenance_reports (work_order_id)
    WHERE status IN ('DRAFT', 'AUTHOR_VERIFIED', 'SUBMITTED');
