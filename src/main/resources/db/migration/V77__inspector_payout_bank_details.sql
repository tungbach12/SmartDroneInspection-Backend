ALTER TABLE inspector_profiles
    ADD COLUMN IF NOT EXISTS payout_bank_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS payout_bank_account_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS payout_bank_account_holder VARCHAR(200),
    ADD COLUMN IF NOT EXISTS payout_bank_branch VARCHAR(200);
