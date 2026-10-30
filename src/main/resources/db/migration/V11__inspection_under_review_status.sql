-- Migration V11: Add UNDER_REVIEW to inspections status check constraint
-- Supports dedicated Under Review lifecycle state when Inspector submits report.

ALTER TABLE inspections DROP CONSTRAINT IF EXISTS ck_inspections_status;

ALTER TABLE inspections ADD CONSTRAINT ck_inspections_status CHECK (
    (status)::text = ANY (
        ARRAY[
            'DRAFT'::character varying,
            'ASSIGNED'::character varying,
            'READY_FOR_FLIGHT'::character varying,
            'IN_PROGRESS'::character varying,
            'FIELD_COMPLETED'::character varying,
            'REPORT_DRAFT'::character varying,
            'UNDER_REVIEW'::character varying,
            'REPORT_PUBLISHED'::character varying,
            'REPAIR_PENDING'::character varying,
            'COMPLETED'::character varying,
            'CANCELLED'::character varying
        ]::text[]
    )
);
