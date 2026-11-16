-- =============================================================================
-- V58: Link cropped / extracted image evidence back to its source video frame
-- =============================================================================

ALTER TABLE evidence
    ADD COLUMN IF NOT EXISTS source_video_evidence_id UUID NULL
        REFERENCES evidence (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS source_timestamp_seconds DOUBLE PRECISION NULL;

CREATE INDEX IF NOT EXISTS idx_evidence_source_video_evidence_id
    ON evidence (source_video_evidence_id)
    WHERE source_video_evidence_id IS NOT NULL;
