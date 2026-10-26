-- =============================================================================
-- V4: Notification inbox deduplication key and index
-- =============================================================================

ALTER TABLE notifications
    ADD COLUMN deduplication_key character varying(192);

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_deduplication_key UNIQUE (deduplication_key);

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_deduplication_key CHECK (
        (deduplication_key IS NULL) OR (btrim((deduplication_key)::text) <> ''::text)
    );

CREATE INDEX ix_notifications_inbox
    ON notifications USING btree (recipient_user_id, organization_id, created_at DESC);
