ALTER TABLE report_versions
    ADD COLUMN client_decision_by_user_id UUID,
    ADD COLUMN client_decision_reason VARCHAR(2000),
    ADD CONSTRAINT fk_report_version_client_decision_user
        FOREIGN KEY (client_decision_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_report_version_client_decision_reason
        CHECK (
            client_decision_reason IS NULL
            OR (status = 'REVISION_REQUESTED' AND BTRIM(client_decision_reason) <> '')
        );
