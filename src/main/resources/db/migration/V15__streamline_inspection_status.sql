-- Migration V15: Streamline InspectionStatus to 6 canonical lifecycle states
-- Drops old check constraint, migrates existing row data, and applies new constraint.

ALTER TABLE inspections DROP CONSTRAINT IF EXISTS ck_inspections_status;

-- Migrate legacy planning and dispatch states
UPDATE inspections
SET status = 'SCHEDULED'
WHERE status IN ('DRAFT', 'ASSIGNED', 'READY_FOR_FLIGHT');

-- Migrate legacy report drafting states
UPDATE inspections
SET status = 'FIELD_COMPLETED'
WHERE status = 'REPORT_DRAFT';

-- Migrate legacy publication and handoff states
UPDATE inspections
SET status = 'COMPLETED'
WHERE status IN ('REPORT_PUBLISHED', 'REPAIR_PENDING');

-- Re-apply check constraint with the 6 statuses
ALTER TABLE inspections ADD CONSTRAINT ck_inspections_status CHECK (
    (status)::text = ANY (
        ARRAY[
            'SCHEDULED'::character varying,
            'IN_PROGRESS'::character varying,
            'FIELD_COMPLETED'::character varying,
            'UNDER_REVIEW'::character varying,
            'COMPLETED'::character varying,
            'CANCELLED'::character varying
        ]::text[]
    )
);
