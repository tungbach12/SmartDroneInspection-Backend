-- Dedicated Inspector <-> Customer inspection chat messages and attachments,
-- Admin-configurable assignment acceptance timeout (default 24 hours),
-- and timeout tracking columns for automatic step-4 occurrence unassignment.

CREATE TABLE inspection_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    inspection_id uuid NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    author_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body character varying(4000) NOT NULL DEFAULT '',
    has_attachments boolean NOT NULL DEFAULT false,
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inspection_messages_body_length CHECK (char_length(body) <= 4000)
);

CREATE INDEX ix_inspection_messages_inspection_created
    ON inspection_messages (inspection_id, created_at, id);

CREATE TABLE inspection_message_attachments (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    message_id uuid NOT NULL REFERENCES inspection_messages(id) ON DELETE CASCADE,
    file_name character varying(255) NOT NULL,
    content_type character varying(120) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    object_key character varying(512) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inspection_message_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    CONSTRAINT ck_inspection_message_attachments_checksum CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_inspection_message_attachments_content_type CHECK (
        content_type IN (
            'image/png',
            'image/jpeg',
            'image/webp',
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        )
    )
);

CREATE INDEX ix_inspection_message_attachments_message
    ON inspection_message_attachments (message_id);

CREATE TABLE platform_operations_settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    assignment_acceptance_timeout_hours integer NOT NULL DEFAULT 24,
    updated_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_platform_operations_settings_timeout_hours
        CHECK (assignment_acceptance_timeout_hours BETWEEN 1 AND 720)
);

INSERT INTO platform_operations_settings (assignment_acceptance_timeout_hours)
VALUES (24);

ALTER TABLE inspections
    ADD COLUMN assignment_requested_at timestamp with time zone;

UPDATE inspections
SET assignment_requested_at = COALESCE(updated_at, created_at)
WHERE assignment_status = 'PENDING_ACCEPTANCE'
  AND status = 'SCHEDULED';

ALTER TABLE service_request_occurrences
    ADD COLUMN last_timeout_cancelled_at timestamp with time zone,
    ADD COLUMN last_timeout_inspector_id uuid REFERENCES users(id) ON DELETE SET NULL;
