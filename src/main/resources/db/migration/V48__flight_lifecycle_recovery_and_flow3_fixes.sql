-- =============================================================================
-- V48: Field session lifecycle recovery (decline, force-abort, overdue/hung monitoring,
--      cancellation reason, reliability ledger) and Flow 3 report clarification tracking.
-- =============================================================================

ALTER TABLE inspections
    ADD COLUMN IF NOT EXISTS reassignment_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS decline_reason VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS declined_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS declined_by_inspector_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS overdue_notified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS clarification_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS ix_inspections_scheduled_overdue
    ON inspections (status, planned_end_at)
    WHERE status = 'SCHEDULED' AND reassignment_required = FALSE;

ALTER TABLE field_sessions
    ADD COLUMN IF NOT EXISTS hung_notified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS aborted_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE inspection_reports
    ADD COLUMN IF NOT EXISTS clarification_requested BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS inspector_reliability_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspector_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    field_session_id UUID REFERENCES field_sessions(id) ON DELETE SET NULL,
    event_type VARCHAR(24) NOT NULL,
    reason VARCHAR(2000),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inspector_reliability_event_type CHECK (
        event_type IN ('DECLINE', 'NO_SHOW', 'ABORT', 'FORCE_ABORT')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reliability_no_show
    ON inspector_reliability_events (inspection_id, inspector_id)
    WHERE event_type = 'NO_SHOW';

CREATE INDEX IF NOT EXISTS ix_reliability_inspector_type
    ON inspector_reliability_events (inspector_id, event_type);
