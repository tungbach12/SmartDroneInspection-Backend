ALTER TABLE service_requests
  ADD COLUMN IF NOT EXISTS inspection_items jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE inspections
  ADD COLUMN IF NOT EXISTS inspection_items jsonb NOT NULL DEFAULT '[]'::jsonb;
