-- Give a RESOLVED defect verification a limited validity window.
--
-- A "RESOLVED" verdict is evidence a defect was fixed. Without an expiry, that verdict can be
-- replayed months later to close work that has since regressed. verified_at records when the
-- Inspector made the determination; valid_until is derived from it at write time.

ALTER TABLE reinspection_defect_verifications
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN valid_until TIMESTAMPTZ;

-- Historical rows already RESOLVED keep their determination time as the update timestamp so the
-- window is anchored rather than open-ended.
UPDATE reinspection_defect_verifications
SET verified_at = updated_at
WHERE resolution_status = 'RESOLVED' AND verified_at IS NULL;

CREATE INDEX ix_reinspection_verif_valid_until
    ON reinspection_defect_verifications (valid_until)
    WHERE resolution_status = 'RESOLVED';