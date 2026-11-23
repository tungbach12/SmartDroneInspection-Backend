-- Dedicated Service Manager <-> assigned Inspector chat per customer service request.
-- Keeps internal operational coordination separate from Customer <-> Manager commercial
-- negotiation (service_request_messages) and Customer <-> Inspector field chat (inspection_messages).

CREATE TABLE service_request_partner_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES service_requests(id) ON DELETE CASCADE,
    inspector_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    author_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body character varying(4000) NOT NULL DEFAULT '',
    has_attachments boolean NOT NULL DEFAULT false,
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sr_partner_messages_body_length CHECK (char_length(body) <= 4000)
);

CREATE INDEX ix_sr_partner_messages_thread_created
    ON service_request_partner_messages (request_id, inspector_id, created_at, id);

CREATE TABLE service_request_partner_message_attachments (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    message_id uuid NOT NULL REFERENCES service_request_partner_messages(id) ON DELETE CASCADE,
    file_name character varying(255) NOT NULL,
    content_type character varying(120) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    object_key character varying(512) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sr_partner_msg_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    CONSTRAINT ck_sr_partner_msg_attachments_checksum CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sr_partner_msg_attachments_content_type CHECK (
        content_type IN (
            'image/png',
            'image/jpeg',
            'image/webp',
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        )
    )
);

CREATE INDEX ix_sr_partner_msg_attachments_message
    ON service_request_partner_message_attachments (message_id);
