-- V23: provider onboarding activation tokens on users.
--
-- Forward-only: V1..V22 are applied and are not edited here. Two nullable columns are added to
-- users so provider registrations can require email activation before login. Both stay NULL for
-- every user not in an activation flow.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS activation_token_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS activation_expires_at TIMESTAMPTZ;
