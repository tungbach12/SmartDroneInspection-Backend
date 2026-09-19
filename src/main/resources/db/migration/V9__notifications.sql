-- Shared user-facing notification delivery records.
-- Notification failure must not roll back the business transition that created it.

CREATE TABLE notifications
(
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    organization_id   UUID        REFERENCES organizations (id) ON DELETE SET NULL,
    event_type        VARCHAR(96) NOT NULL,
    channel           VARCHAR(16) NOT NULL,
    title             VARCHAR(300) NOT NULL,
    body              VARCHAR(4000) NOT NULL,
    target_path       VARCHAR(1000),
    status            VARCHAR(24) NOT NULL,
    attempt_count     INTEGER     NOT NULL DEFAULT 0,
    last_error_code   VARCHAR(96),
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_event_type CHECK (BTRIM(event_type) <> ''),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('IN_APP', 'EMAIL')),
    CONSTRAINT ck_notifications_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_notifications_body CHECK (BTRIM(body) <> ''),
    CONSTRAINT ck_notifications_target_path CHECK (
        target_path IS NULL OR BTRIM(target_path) <> ''
    ),
    CONSTRAINT ck_notifications_status CHECK (
        status IN ('PENDING', 'SENT', 'FAILED', 'READ')
    ),
    CONSTRAINT ck_notifications_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_notifications_read_state CHECK (
        (status = 'READ' AND sent_at IS NOT NULL AND read_at IS NOT NULL)
        OR (status <> 'READ' AND read_at IS NULL)
    )
);

CREATE INDEX ix_notifications_recipient_status
    ON notifications (recipient_user_id, status, created_at DESC);
CREATE INDEX ix_notifications_delivery
    ON notifications (status, created_at);
