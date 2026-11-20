ALTER TABLE platform_ai_settings
    ALTER COLUMN auto_confirm_threshold SET DEFAULT 70.00;

UPDATE platform_ai_settings
SET auto_confirm_threshold = 70.00,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1
  AND (updated_by IS NULL OR auto_confirm_threshold = 85.00);
