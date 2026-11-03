-- =============================================================================
-- V28: Persist SmartDroneHub Flight Report Metrics, Flight Trace, and Video Link
-- =============================================================================

ALTER TABLE field_sessions
    ADD COLUMN IF NOT EXISTS telemetry_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS flight_duration_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS distance_flown_meters NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS max_altitude_meters NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS start_battery_percent INTEGER,
    ADD COLUMN IF NOT EXISTS end_battery_percent INTEGER,
    ADD COLUMN IF NOT EXISTS flight_final_state VARCHAR(32),
    ADD COLUMN IF NOT EXISTS flight_report_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS flight_trace_json TEXT,
    ADD COLUMN IF NOT EXISTS flight_video_evidence_id UUID REFERENCES evidence(id) ON DELETE SET NULL;
