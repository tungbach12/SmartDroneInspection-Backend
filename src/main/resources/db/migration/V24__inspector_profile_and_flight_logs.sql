-- =============================================================================
-- V20: Inspector Partner Profile, Scanned Flight Licenses, and Flight Logs
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS citizen_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pilot_license_issued_date DATE,
    ADD COLUMN IF NOT EXISTS pilot_license_authority VARCHAR(200),
    ADD COLUMN IF NOT EXISTS prior_flight_hours NUMERIC(8,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS certified_drone_models VARCHAR(500),
    ADD COLUMN IF NOT EXISTS specializations VARCHAR(500),
    ADD COLUMN IF NOT EXISTS experience_summary TEXT,
    ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE TABLE IF NOT EXISTS inspector_flight_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspector_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    flight_code VARCHAR(64) NOT NULL,
    flight_date DATE NOT NULL,
    mission_title VARCHAR(255) NOT NULL,
    drone_model VARCHAR(255) NOT NULL,
    drone_serial_number VARCHAR(128),
    location VARCHAR(500) NOT NULL,
    flight_duration_minutes INT NOT NULL CHECK (flight_duration_minutes > 0),
    inspection_type VARCHAR(128) NOT NULL,
    safety_incident BOOLEAN NOT NULL DEFAULT false,
    notes TEXT,
    recorded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_inspector_flight_logs_inspector ON inspector_flight_logs (inspector_user_id, flight_date DESC);
