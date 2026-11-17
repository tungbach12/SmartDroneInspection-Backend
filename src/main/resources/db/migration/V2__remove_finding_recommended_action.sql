-- Unify findings technical_notes and recommended_action into a single Note column (technical_notes).
UPDATE findings
SET technical_notes = CASE
  WHEN technical_notes IS NOT NULL AND BTRIM(technical_notes) <> ''
    AND recommended_action IS NOT NULL AND BTRIM(recommended_action) <> ''
    AND BTRIM(technical_notes) <> BTRIM(recommended_action)
    THEN BTRIM(technical_notes) || ' · ' || BTRIM(recommended_action)
  WHEN (technical_notes IS NULL OR BTRIM(technical_notes) = '')
    AND recommended_action IS NOT NULL AND BTRIM(recommended_action) <> ''
    THEN BTRIM(recommended_action)
  ELSE technical_notes
END
WHERE recommended_action IS NOT NULL AND BTRIM(recommended_action) <> '';

ALTER TABLE findings
  DROP COLUMN IF EXISTS recommended_action;
