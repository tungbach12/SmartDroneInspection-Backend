ALTER TABLE platform_operations_settings
    ADD COLUMN api_request_timeout_seconds integer NOT NULL DEFAULT 120;

ALTER TABLE platform_operations_settings
    ADD CONSTRAINT ck_platform_operations_settings_api_timeout_seconds
        CHECK (api_request_timeout_seconds BETWEEN 10 AND 600);
