-- MF3: one canonical asset location belongs to the capture, so every finding from that image
-- shares it. Legacy finding location remains nullable for old records but is no longer the source
-- of truth.
ALTER TABLE evidence
    ADD COLUMN location_description VARCHAR(1000);

ALTER TABLE verified_findings
    ALTER COLUMN location_description DROP NOT NULL;

-- MF3-06: the Inspector's findings confirmation is distinct from the Organization Admin's report
-- approval. Preserve existing explicit reviewer confirmations as confirmation history; do not
-- promote rejected or pending finding proposals.
ALTER TABLE verified_findings
    ADD COLUMN inspector_confirmed_at TIMESTAMPTZ;

UPDATE verified_findings
   SET inspector_confirmed_at = COALESCE(decided_at, created_at)
 WHERE decision IN ('CONFIRMED', 'MODIFIED');

-- Inspector preference is global to that Inspector's account and defaults to automatic fallback.
ALTER TABLE users
    ADD COLUMN vision_fallback_enabled BOOLEAN NOT NULL DEFAULT TRUE;
