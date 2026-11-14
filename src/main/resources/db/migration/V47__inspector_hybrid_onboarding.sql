-- =============================================================================
-- V33: Inspector Partner Hybrid Onboarding (Online Self-Registration,
--      Signed Partner Contract with SHA-256 Integrity & Manager Approval Gate)
-- =============================================================================

ALTER TABLE inspector_profiles
    ADD COLUMN IF NOT EXISTS onboarding_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS partner_contract_file_key VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS partner_contract_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS partner_contract_uploaded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
