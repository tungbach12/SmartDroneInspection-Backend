ALTER TABLE service_request_messages
    ALTER COLUMN body SET DEFAULT '';

ALTER TABLE service_request_messages
    DROP CONSTRAINT ck_service_request_messages_body;

ALTER TABLE service_request_messages
    ADD CONSTRAINT ck_service_request_messages_body
        CHECK (length(btrim(body)) <= 4000);

CREATE TABLE service_request_message_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_request_message_attachments_message
        FOREIGN KEY (message_id) REFERENCES service_request_messages(id) ON DELETE CASCADE,
    CONSTRAINT ck_service_request_message_attachments_content_type
        CHECK (content_type IN (
            'image/png', 'image/jpeg', 'image/webp',
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document')),
    CONSTRAINT ck_service_request_message_attachments_size
        CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    CONSTRAINT ck_service_request_message_attachments_checksum
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_service_request_message_attachments_message
    ON service_request_message_attachments (message_id, created_at, id);

CREATE FUNCTION enforce_service_request_message_body_or_attachment()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_message_id UUID;
    target_body TEXT;
BEGIN
    IF TG_TABLE_NAME = 'service_request_messages' THEN
        target_message_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_message_id := COALESCE(NEW.message_id, OLD.message_id);
    END IF;
    SELECT body INTO target_body
      FROM service_request_messages
      WHERE id = target_message_id;

    IF FOUND
       AND length(btrim(target_body)) = 0
       AND NOT EXISTS (
           SELECT 1
             FROM service_request_message_attachments
             WHERE message_id = target_message_id
       ) THEN
        RAISE EXCEPTION 'A service request message requires text or at least one attachment'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_service_request_messages_body_or_attachment';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ck_service_request_messages_body_or_attachment
AFTER INSERT OR UPDATE ON service_request_messages
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_service_request_message_body_or_attachment();

CREATE CONSTRAINT TRIGGER ck_service_request_message_attachments_body_or_text
AFTER INSERT OR DELETE ON service_request_message_attachments
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_service_request_message_body_or_attachment();
