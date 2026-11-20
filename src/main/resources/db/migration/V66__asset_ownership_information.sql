-- Ownership information is free text, not structured technical data.
-- Keep it in its own column so the jsonb technical_profile column is not
-- asked to accept a raw string (which PostgreSQL rejects as invalid JSON).
ALTER TABLE assets ADD COLUMN ownership_information character varying(1000);

-- Preserve ownership values that earlier code mistakenly stored in
-- technical_profile. A JSON string is unwrapped; any other JSON value keeps
-- its textual representation.
UPDATE assets
SET ownership_information = CASE
    WHEN jsonb_typeof(technical_profile) = 'string' THEN technical_profile #>> '{}'
    ELSE technical_profile::text
END
WHERE ownership_information IS NULL
  AND technical_profile IS NOT NULL;
