-- Add read_at timestamp to track when messages were viewed by the recipient.
ALTER TABLE service_request_messages
    ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX ix_service_request_messages_unread
    ON service_request_messages (request_id, read_at)
    WHERE read_at IS NULL;
