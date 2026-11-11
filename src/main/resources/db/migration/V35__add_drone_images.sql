-- =============================================================================
-- V34: Drone Image Evidence Keys
-- =============================================================================

ALTER TABLE drone_registration_applications
    ADD COLUMN IF NOT EXISTS image_file_keys JSONB;

ALTER TABLE drones
    ADD COLUMN IF NOT EXISTS image_file_keys JSONB;
