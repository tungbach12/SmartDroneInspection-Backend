-- Enable recording multiple flights / sorties per field session (across single or multiple days)
-- without overwriting earlier telemetry, flight traces, or synced flight videos.

CREATE TABLE IF NOT EXISTS field_session_flights (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    field_session_id uuid NOT NULL REFERENCES field_sessions(id) ON DELETE CASCADE,
    inspection_id uuid NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    flight_number integer NOT NULL,
    flight_date date NOT NULL,
    flight_label character varying(160),
    telemetry_source character varying(64),
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    flight_duration_seconds integer,
    distance_flown_meters numeric(10,2),
    max_altitude_meters numeric(8,2),
    start_battery_percent integer,
    end_battery_percent integer,
    flight_final_state character varying(32),
    flight_report_note character varying(1000),
    flight_trace_json text,
    flight_video_evidence_id uuid REFERENCES evidence(id) ON DELETE SET NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_field_session_flights_number UNIQUE (field_session_id, flight_number),
    CONSTRAINT ck_field_session_flights_number CHECK (flight_number >= 1)
);

CREATE INDEX IF NOT EXISTS ix_field_session_flights_inspection
    ON field_session_flights (inspection_id, flight_date ASC, started_at ASC, flight_number ASC);

CREATE INDEX IF NOT EXISTS ix_field_session_flights_session
    ON field_session_flights (field_session_id, flight_number ASC);

INSERT INTO field_session_flights (
    field_session_id,
    inspection_id,
    flight_number,
    flight_date,
    flight_label,
    telemetry_source,
    started_at,
    ended_at,
    flight_duration_seconds,
    distance_flown_meters,
    max_altitude_meters,
    start_battery_percent,
    end_battery_percent,
    flight_final_state,
    flight_report_note,
    flight_trace_json,
    flight_video_evidence_id,
    created_at,
    updated_at
)
SELECT
    fst.field_session_id,
    fs.inspection_id,
    1,
    COALESCE(fs.session_date, (fs.started_at AT TIME ZONE 'UTC')::date, (fs.created_at AT TIME ZONE 'UTC')::date),
    'Flight #1',
    fst.telemetry_source,
    fs.started_at,
    fs.ended_at,
    fst.flight_duration_seconds,
    fst.distance_flown_meters,
    fst.max_altitude_meters,
    fst.start_battery_percent,
    fst.end_battery_percent,
    fst.flight_final_state,
    fst.flight_report_note,
    fst.flight_trace_json,
    fst.flight_video_evidence_id,
    fs.created_at,
    fs.updated_at
FROM field_session_telemetry fst
JOIN field_sessions fs ON fs.id = fst.field_session_id
WHERE fst.telemetry_source IS NOT NULL
  AND fst.telemetry_source <> 'NONE'
ON CONFLICT (field_session_id, flight_number) DO NOTHING;
