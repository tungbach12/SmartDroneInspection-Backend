-- =============================================================================
-- V14: Add repair proof image and engineer notes to reinspection_defect_verifications
-- =============================================================================

ALTER TABLE reinspection_defect_verifications
    ADD COLUMN IF NOT EXISTS repair_proof_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS repair_engineer_notes VARCHAR(4000);

-- Populate demo data for existing re-inspection defect verification if present
UPDATE reinspection_defect_verifications
SET repair_proof_url = 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=800&q=80',
    repair_engineer_notes = 'Thi công trám vết nứt bằng keo epoxy Sikadur-731 chịu lực áp lực cao. Bề mặt đã được mài phẳng và phủ lớp sơn bảo vệ chống thấm nước.'
WHERE repair_proof_url IS NULL;
