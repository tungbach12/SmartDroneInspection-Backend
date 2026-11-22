-- Normalize overloaded lifecycle tables into cohesive 1:1 satellite tables:
-- 1) inspection_flight_permits (18 flight_permit_* columns extracted from inspections)
-- 2) field_session_telemetry (10 SmartDroneHub flight telemetry columns extracted from field_sessions)

CREATE TABLE inspection_flight_permits (
    inspection_id uuid NOT NULL,
    file_key character varying(1000),
    file_name character varying(500),
    permit_number character varying(120),
    authority character varying(200),
    valid_from timestamp with time zone,
    valid_to timestamp with time zone,
    drone_serial character varying(120),
    max_altitude_m integer,
    file_sha256 character varying(64),
    file_size_bytes bigint,
    content_type character varying(120),
    uploaded_at timestamp with time zone,
    uploaded_by uuid,
    verification_status character varying(32) DEFAULT 'NOT_UPLOADED'::character varying NOT NULL,
    verified_by uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(1000),
    review_notes character varying(1000),
    CONSTRAINT inspection_flight_permits_pkey PRIMARY KEY (inspection_id),
    CONSTRAINT fk_inspection_flight_permits_inspection FOREIGN KEY (inspection_id)
        REFERENCES inspections(id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_flight_permits_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_inspection_flight_permits_verified_by FOREIGN KEY (verified_by)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_inspection_flight_permits_altitude CHECK (
        max_altitude_m IS NULL OR (max_altitude_m > 0 AND max_altitude_m <= 2000)),
    CONSTRAINT ck_inspection_flight_permits_size CHECK (
        file_size_bytes IS NULL OR file_size_bytes > 0),
    CONSTRAINT ck_inspection_flight_permits_verification_status CHECK (
        verification_status IN ('NOT_UPLOADED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_inspection_flight_permits_window CHECK (
        valid_from IS NULL OR valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX ix_inspection_flight_permits_pending_review
    ON inspection_flight_permits USING btree (verification_status)
    WHERE verification_status = 'PENDING_REVIEW';

INSERT INTO inspection_flight_permits (
    inspection_id,
    file_key,
    file_name,
    permit_number,
    authority,
    valid_from,
    valid_to,
    drone_serial,
    max_altitude_m,
    file_sha256,
    file_size_bytes,
    content_type,
    uploaded_at,
    uploaded_by,
    verification_status,
    verified_by,
    verified_at,
    rejection_reason,
    review_notes
)
SELECT
    id,
    flight_permit_file_key,
    flight_permit_file_name,
    flight_permit_number,
    flight_permit_authority,
    flight_permit_valid_from,
    flight_permit_valid_to,
    flight_permit_drone_serial,
    flight_permit_max_altitude_m,
    flight_permit_file_sha256,
    flight_permit_file_size_bytes,
    flight_permit_content_type,
    flight_permit_uploaded_at,
    flight_permit_uploaded_by,
    flight_permit_verification_status,
    flight_permit_verified_by,
    flight_permit_verified_at,
    flight_permit_rejection_reason,
    flight_permit_review_notes
FROM inspections
WHERE flight_permit_file_key IS NOT NULL
   OR flight_permit_file_name IS NOT NULL
   OR flight_permit_number IS NOT NULL
   OR flight_permit_uploaded_at IS NOT NULL
   OR flight_permit_verification_status <> 'NOT_UPLOADED';

DROP INDEX IF EXISTS ix_inspections_permit_pending_review;

ALTER TABLE inspections
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_altitude,
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_size,
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_verification_status,
    DROP CONSTRAINT IF EXISTS ck_inspections_flight_permit_window,
    DROP CONSTRAINT IF EXISTS inspections_flight_permit_uploaded_by_fkey,
    DROP CONSTRAINT IF EXISTS inspections_flight_permit_verified_by_fkey,
    DROP COLUMN IF EXISTS flight_permit_file_key,
    DROP COLUMN IF EXISTS flight_permit_file_name,
    DROP COLUMN IF EXISTS flight_permit_number,
    DROP COLUMN IF EXISTS flight_permit_authority,
    DROP COLUMN IF EXISTS flight_permit_valid_from,
    DROP COLUMN IF EXISTS flight_permit_valid_to,
    DROP COLUMN IF EXISTS flight_permit_drone_serial,
    DROP COLUMN IF EXISTS flight_permit_max_altitude_m,
    DROP COLUMN IF EXISTS flight_permit_file_sha256,
    DROP COLUMN IF EXISTS flight_permit_file_size_bytes,
    DROP COLUMN IF EXISTS flight_permit_content_type,
    DROP COLUMN IF EXISTS flight_permit_uploaded_at,
    DROP COLUMN IF EXISTS flight_permit_uploaded_by,
    DROP COLUMN IF EXISTS flight_permit_verification_status,
    DROP COLUMN IF EXISTS flight_permit_verified_by,
    DROP COLUMN IF EXISTS flight_permit_verified_at,
    DROP COLUMN IF EXISTS flight_permit_rejection_reason,
    DROP COLUMN IF EXISTS flight_permit_review_notes;

CREATE TABLE field_session_telemetry (
    field_session_id uuid NOT NULL,
    telemetry_source character varying(64),
    flight_duration_seconds integer,
    distance_flown_meters numeric(10,2),
    max_altitude_meters numeric(8,2),
    start_battery_percent integer,
    end_battery_percent integer,
    flight_final_state character varying(32),
    flight_report_note character varying(1000),
    flight_trace_json text,
    flight_video_evidence_id uuid,
    CONSTRAINT field_session_telemetry_pkey PRIMARY KEY (field_session_id),
    CONSTRAINT fk_field_session_telemetry_session FOREIGN KEY (field_session_id)
        REFERENCES field_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_field_session_telemetry_video_evidence FOREIGN KEY (flight_video_evidence_id)
        REFERENCES evidence(id) ON DELETE SET NULL
);

INSERT INTO field_session_telemetry (
    field_session_id,
    telemetry_source,
    flight_duration_seconds,
    distance_flown_meters,
    max_altitude_meters,
    start_battery_percent,
    end_battery_percent,
    flight_final_state,
    flight_report_note,
    flight_trace_json,
    flight_video_evidence_id
)
SELECT
    id,
    telemetry_source,
    flight_duration_seconds,
    distance_flown_meters,
    max_altitude_meters,
    start_battery_percent,
    end_battery_percent,
    flight_final_state,
    flight_report_note,
    flight_trace_json,
    flight_video_evidence_id
FROM field_sessions
WHERE telemetry_source IS NOT NULL
   OR flight_duration_seconds IS NOT NULL
   OR distance_flown_meters IS NOT NULL
   OR max_altitude_meters IS NOT NULL
   OR start_battery_percent IS NOT NULL
   OR end_battery_percent IS NOT NULL
   OR flight_final_state IS NOT NULL
   OR flight_report_note IS NOT NULL
   OR flight_trace_json IS NOT NULL
   OR flight_video_evidence_id IS NOT NULL;

ALTER TABLE field_sessions
    DROP CONSTRAINT IF EXISTS field_sessions_flight_video_evidence_id_fkey,
    DROP COLUMN IF EXISTS telemetry_source,
    DROP COLUMN IF EXISTS flight_duration_seconds,
    DROP COLUMN IF EXISTS distance_flown_meters,
    DROP COLUMN IF EXISTS max_altitude_meters,
    DROP COLUMN IF EXISTS start_battery_percent,
    DROP COLUMN IF EXISTS end_battery_percent,
    DROP COLUMN IF EXISTS flight_final_state,
    DROP COLUMN IF EXISTS flight_report_note,
    DROP COLUMN IF EXISTS flight_trace_json,
    DROP COLUMN IF EXISTS flight_video_evidence_id;
