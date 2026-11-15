ALTER TABLE evidence
  ADD COLUMN IF NOT EXISTS original_size_bytes BIGINT,
  ADD COLUMN IF NOT EXISTS processing_status VARCHAR(32) NOT NULL DEFAULT 'READY',
  ADD COLUMN IF NOT EXISTS processing_error TEXT,
  ADD COLUMN IF NOT EXISTS raw_object_key VARCHAR(1000),
  ADD COLUMN IF NOT EXISTS thumbnail_object_key VARCHAR(1000),
  ADD COLUMN IF NOT EXISTS thumbnail_size_bytes BIGINT,
  ADD COLUMN IF NOT EXISTS media_width INTEGER,
  ADD COLUMN IF NOT EXISTS media_height INTEGER,
  ADD COLUMN IF NOT EXISTS duration_seconds DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS processing_attempts INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS processing_locked_at TIMESTAMPTZ;

ALTER TABLE evidence
  ADD CONSTRAINT ck_evidence_processing_status
    CHECK (processing_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'));

CREATE INDEX IF NOT EXISTS ix_evidence_processing_queue
  ON evidence (processing_status, processing_locked_at, created_at);

CREATE TABLE evidence_upload_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    field_session_id UUID REFERENCES field_sessions(id) ON DELETE RESTRICT,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    chunk_size_bytes INTEGER NOT NULL,
    total_parts INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    storage_upload_id VARCHAR(500),
    raw_object_key VARCHAR(1000) NOT NULL,
    evidence_id UUID REFERENCES evidence(id) ON DELETE SET NULL,
    capture_time TIMESTAMPTZ,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    location_description VARCHAR(1000),
    uploaded_parts_json TEXT NOT NULL DEFAULT '[]',
    error_message TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_evidence_upload_sessions_status
      CHECK (status IN ('OPEN', 'COMPLETING', 'PROCESSING', 'READY', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_evidence_upload_sessions_sizes
      CHECK (declared_size_bytes > 0 AND chunk_size_bytes > 0 AND total_parts > 0),
    CONSTRAINT ck_evidence_upload_sessions_lat
      CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT ck_evidence_upload_sessions_lon
      CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

CREATE INDEX ix_evidence_upload_sessions_inspection_status
  ON evidence_upload_sessions (inspection_id, status);

CREATE INDEX ix_evidence_upload_sessions_expires_at
  ON evidence_upload_sessions (status, expires_at);

CREATE INDEX ix_evidence_upload_sessions_evidence_id
  ON evidence_upload_sessions (evidence_id);
