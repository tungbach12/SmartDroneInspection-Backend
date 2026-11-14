ALTER TABLE inspector_applications
  ADD COLUMN IF NOT EXISTS signed_contract_file_key VARCHAR(1000),
  ADD COLUMN IF NOT EXISTS signed_contract_sha256 VARCHAR(128);
