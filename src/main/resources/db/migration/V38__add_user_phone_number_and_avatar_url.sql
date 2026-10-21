-- Personnel identity enhancement: optional contact phone and avatar URL for field coordination and profile UI.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
