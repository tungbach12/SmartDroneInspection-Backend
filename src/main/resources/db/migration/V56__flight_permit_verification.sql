-- =============================================================================
-- V56: Flight Permit Structured Metadata, Integrity & Mandatory Manager Review
-- =============================================================================

ALTER TABLE inspections
    ADD COLUMN IF NOT EXISTS flight_permit_number VARCHAR(120),
    ADD COLUMN IF NOT EXISTS flight_permit_authority VARCHAR(200),
    ADD COLUMN IF NOT EXISTS flight_permit_valid_from TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS flight_permit_valid_to TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS flight_permit_drone_serial VARCHAR(120),
    ADD COLUMN IF NOT EXISTS flight_permit_max_altitude_m INTEGER,
    ADD COLUMN IF NOT EXISTS flight_permit_file_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS flight_permit_file_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS flight_permit_content_type VARCHAR(120),
    ADD COLUMN IF NOT EXISTS flight_permit_uploaded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS flight_permit_uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS flight_permit_verification_status VARCHAR(32) NOT NULL DEFAULT 'NOT_UPLOADED',
    ADD COLUMN IF NOT EXISTS flight_permit_verified_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS flight_permit_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS flight_permit_rejection_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS flight_permit_review_notes VARCHAR(1000);

-- Backfill existing records safely:
-- Historical/active flights with an existing permit key are marked APPROVED;
-- scheduled/cancelled missions with an attached permit key enter PENDING_REVIEW.
UPDATE inspections
SET flight_permit_verification_status = CASE
        WHEN flight_permit_file_key IS NOT NULL AND btrim(flight_permit_file_key) <> '' THEN
            CASE
                WHEN status IN ('IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW', 'COMPLETED') THEN 'APPROVED'
                ELSE 'PENDING_REVIEW'
            END
        ELSE 'NOT_UPLOADED'
    END,
    flight_permit_uploaded_at = COALESCE(
        flight_permit_uploaded_at,
        CASE
            WHEN flight_permit_file_key IS NOT NULL AND btrim(flight_permit_file_key) <> '' THEN updated_at
            ELSE NULL
        END
    );

ALTER TABLE inspections
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_verification_status,
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_window,
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_altitude,
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_size;

ALTER TABLE inspections
    ADD CONSTRAINT ck_inspections_flight_permit_verification_status
        CHECK (flight_permit_verification_status IN ('NOT_UPLOADED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_inspections_flight_permit_window
        CHECK (flight_permit_valid_from IS NULL OR flight_permit_valid_to IS NULL OR flight_permit_valid_to > flight_permit_valid_from),
    ADD CONSTRAINT ck_inspections_flight_permit_altitude
        CHECK (flight_permit_max_altitude_m IS NULL OR (flight_permit_max_altitude_m > 0 AND flight_permit_max_altitude_m <= 2000)),
    ADD CONSTRAINT ck_inspections_flight_permit_size
        CHECK (flight_permit_file_size_bytes IS NULL OR flight_permit_file_size_bytes > 0);

CREATE INDEX IF NOT EXISTS ix_inspections_permit_pending_review
    ON inspections (flight_permit_verification_status)
    WHERE flight_permit_verification_status = 'PENDING_REVIEW';
