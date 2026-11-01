-- =============================================================================
-- V25: Database Schema Cleanup & Consolidation
-- 1. Drop 7 legacy/superseded tables (subscriptions, payment_transactions,
--    platform_drones, drone_leases, recurring_inspection_schedules,
--    schedule_occurrences, inspector_partner_applications) and unused sequence.
-- 2. Extract 14 inspector-only profile/license columns from users into
--    inspector_profiles, and drop dead login columns (35 -> 19 columns).
-- 3. Consolidate login_otp_challenges, registration_otp_challenges, and
--    password_reset_otp_challenges into a single otp_challenges table.
-- 4. Drop dead/write-only/unmapped columns and duplicate occurrence assignment
--    columns across remaining domain tables.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Detach FKs and drop 7 legacy tables + unused PayOS sequence
-- -----------------------------------------------------------------------------

DROP INDEX IF EXISTS ix_drones_lease_id;

ALTER TABLE drones
    DROP CONSTRAINT IF EXISTS ck_drones_ownership_type,
    DROP COLUMN IF EXISTS ownership_type,
    DROP COLUMN IF EXISTS lease_id,
    DROP COLUMN IF EXISTS lease_expires_at,
    DROP COLUMN IF EXISTS last_maintenance_at,
    DROP COLUMN IF EXISTS next_maintenance_at;

ALTER TABLE inspections
    DROP CONSTRAINT IF EXISTS fk_inspections_recurring_schedule,
    DROP CONSTRAINT IF EXISTS ck_inspections_partner_drone,
    DROP COLUMN IF EXISTS recurring_schedule_id,
    DROP COLUMN IF EXISTS flight_permit_uploaded_at;

DROP TABLE IF EXISTS payment_transactions;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS drone_leases;
DROP TABLE IF EXISTS platform_drones;
DROP TABLE IF EXISTS schedule_occurrences;
DROP TABLE IF EXISTS recurring_inspection_schedules;
DROP TABLE IF EXISTS inspector_partner_applications;

DROP SEQUENCE IF EXISTS service_request_occurrences_payos_order_code_seq;

-- -----------------------------------------------------------------------------
-- 2. Extract 14 Inspector Profile & License columns from users -> inspector_profiles
-- -----------------------------------------------------------------------------

CREATE TABLE inspector_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    pilot_license_number VARCHAR(128),
    pilot_license_class VARCHAR(64),
    pilot_license_expires_at TIMESTAMPTZ,
    pilot_license_file_key VARCHAR(1000),
    pilot_license_issued_date DATE,
    pilot_license_authority VARCHAR(200),
    citizen_id VARCHAR(32),
    date_of_birth DATE,
    address VARCHAR(500),
    prior_flight_hours NUMERIC(8,2) NOT NULL DEFAULT 0.00,
    certified_drone_models VARCHAR(500),
    specializations VARCHAR(500),
    experience_summary TEXT,
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO inspector_profiles (
    user_id,
    pilot_license_number,
    pilot_license_class,
    pilot_license_expires_at,
    pilot_license_file_key,
    pilot_license_issued_date,
    pilot_license_authority,
    citizen_id,
    date_of_birth,
    address,
    prior_flight_hours,
    certified_drone_models,
    specializations,
    experience_summary,
    notes,
    updated_at
)
SELECT
    id,
    pilot_license_number,
    pilot_license_class,
    pilot_license_expires_at,
    pilot_license_file_key,
    pilot_license_issued_date,
    pilot_license_authority,
    citizen_id,
    date_of_birth,
    address,
    COALESCE(prior_flight_hours, 0.00),
    certified_drone_models,
    specializations,
    experience_summary,
    notes,
    updated_at
FROM users
WHERE roles LIKE '%INSPECTOR%'
   OR actor_zone = 'INSPECTOR_PARTNER'
   OR pilot_license_number IS NOT NULL;

ALTER TABLE users
    DROP COLUMN IF EXISTS pilot_license_number,
    DROP COLUMN IF EXISTS pilot_license_class,
    DROP COLUMN IF EXISTS pilot_license_expires_at,
    DROP COLUMN IF EXISTS pilot_license_file_key,
    DROP COLUMN IF EXISTS pilot_license_issued_date,
    DROP COLUMN IF EXISTS pilot_license_authority,
    DROP COLUMN IF EXISTS citizen_id,
    DROP COLUMN IF EXISTS date_of_birth,
    DROP COLUMN IF EXISTS address,
    DROP COLUMN IF EXISTS prior_flight_hours,
    DROP COLUMN IF EXISTS certified_drone_models,
    DROP COLUMN IF EXISTS specializations,
    DROP COLUMN IF EXISTS experience_summary,
    DROP COLUMN IF EXISTS notes,
    DROP COLUMN IF EXISTS last_login_at,
    DROP COLUMN IF EXISTS last_login_ip;

-- -----------------------------------------------------------------------------
-- 3. Consolidate 3 OTP Challenge tables into otp_challenges
-- -----------------------------------------------------------------------------

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose VARCHAR(32) NOT NULL,
    challenge_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    destination VARCHAR(255),
    channel VARCHAR(16),
    client_ip VARCHAR(45),
    code_hash VARCHAR(64),
    code_expires_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    send_count INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_otp_challenges_purpose CHECK (purpose IN ('LOGIN', 'REGISTRATION', 'PASSWORD_RESET'))
);

CREATE INDEX ix_otp_challenges_user_purpose_created
    ON otp_challenges (user_id, purpose, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX ix_otp_challenges_dest_purpose_created
    ON otp_challenges (destination, purpose, created_at DESC)
    WHERE destination IS NOT NULL;

CREATE INDEX ix_otp_challenges_client_ip_created
    ON otp_challenges (client_ip, created_at DESC)
    WHERE client_ip IS NOT NULL;

INSERT INTO otp_challenges (
    id, purpose, challenge_hash, user_id, client_ip, channel,
    code_hash, code_expires_at, expires_at, consumed_at,
    failed_attempts, send_count, last_sent_at, created_at, row_version
)
SELECT
    id, 'LOGIN', challenge_hash, user_id, client_ip, channel,
    code_hash, code_expires_at, expires_at, consumed_at,
    failed_attempts, send_count, last_sent_at, created_at, row_version
FROM login_otp_challenges;

INSERT INTO otp_challenges (
    id, purpose, challenge_hash, destination, channel,
    code_hash, expires_at, consumed_at, failed_attempts, created_at, row_version
)
SELECT
    id, 'REGISTRATION', challenge_hash, destination, channel,
    code_hash, expires_at, consumed_at, failed_attempts, created_at, row_version
FROM registration_otp_challenges;

INSERT INTO otp_challenges (
    id, purpose, challenge_hash, destination, channel,
    code_hash, expires_at, consumed_at, failed_attempts, created_at, row_version
)
SELECT
    id, 'PASSWORD_RESET', challenge_hash, email, 'EMAIL',
    code_hash, expires_at, consumed_at, failed_attempts, created_at, row_version
FROM password_reset_otp_challenges;

DROP TABLE IF EXISTS login_otp_challenges;
DROP TABLE IF EXISTS registration_otp_challenges;
DROP TABLE IF EXISTS password_reset_otp_challenges;

-- -----------------------------------------------------------------------------
-- 4. Drop dead/write-only/unmapped & duplicate columns across remaining tables
-- -----------------------------------------------------------------------------

ALTER TABLE organizations
    DROP CONSTRAINT IF EXISTS organizations_created_by_user_id_fkey,
    DROP COLUMN IF EXISTS legal_name,
    DROP COLUMN IF EXISTS timezone,
    DROP COLUMN IF EXISTS created_by_user_id,
    DROP COLUMN IF EXISTS row_version;

-- Auth session issue/revocation metadata remain persisted and mapped because
-- rotation, revocation audits and integration tests consume those fields.

-- technical_profile and asset_documents.uploaded_by_user_id remain persisted
-- for ownership/document audit information.

ALTER TABLE asset_categories
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS updated_at;

-- Keep service request creator attribution: it remains part of the active API/domain contract.

-- Keep inspector_id/drone_id on the occurrence: payout and assignment workflows
-- still consume these snapshots directly. Their values are maintained alongside
-- inspections by the contract assignment workflow.


-- POSTPONED remains accepted by the legacy status constraint only; the app no
-- longer writes the postponement_reason column, which is retired here.
ALTER TABLE field_sessions
    DROP COLUMN IF EXISTS postponement_reason;

-- Keep inspection report rendered checksums: report integrity metadata is persisted
-- and asserted by the current report workflow/tests.

-- Preserve the reinspection proof fields and MF4 workflow columns; they remain
-- part of active responses and work-order/task/acceptance behavior.

-- Keep created_at/updated_at for change-order lifecycle audit; the entity maps
-- and updates both timestamps.

ALTER TABLE maintenance_work_logs
    DROP COLUMN IF EXISTS updated_at;

-- Keep maintenance_reports.created_at: report versions map it as an immutable
-- audit timestamp and initialize it when a report version is created.

-- Keep `embedding` because the repository still exposes vector similarity queries.
ALTER TABLE repair_knowledge_chunks
    DROP COLUMN IF EXISTS reference_url,
    DROP COLUMN IF EXISTS created_at;

DROP INDEX IF EXISTS ix_notifications_delivery;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_attempts,
    DROP CONSTRAINT IF EXISTS ck_notifications_channel,
    DROP CONSTRAINT IF EXISTS ck_notifications_read_state,
    DROP CONSTRAINT IF EXISTS ck_notifications_status,
    DROP COLUMN IF EXISTS aggregate_type,
    DROP COLUMN IF EXISTS aggregate_id,
    DROP COLUMN IF EXISTS attempt_count,
    DROP COLUMN IF EXISTS last_error_code,
    ADD CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ')),
    ADD CONSTRAINT ck_notifications_read_state CHECK (
        (status = 'READ' AND sent_at IS NOT NULL AND read_at IS NOT NULL)
        OR (status <> 'READ' AND read_at IS NULL)
    );
