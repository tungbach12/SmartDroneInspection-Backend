ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS registration_otp_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_hash VARCHAR(64) NOT NULL UNIQUE,
    destination VARCHAR(255) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_registration_otp_challenges_dest
    ON registration_otp_challenges (destination, created_at DESC);
