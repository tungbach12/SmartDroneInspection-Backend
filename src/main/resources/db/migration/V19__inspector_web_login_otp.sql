CREATE TABLE login_otp_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_ip VARCHAR(45),
    channel VARCHAR(16),
    code_hash VARCHAR(64),
    code_expires_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    send_count INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_login_otp_challenges_user_created
    ON login_otp_challenges (user_id, created_at DESC);
