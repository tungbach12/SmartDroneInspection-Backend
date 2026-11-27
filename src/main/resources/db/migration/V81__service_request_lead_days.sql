ALTER TABLE platform_operations_settings
    ADD COLUMN service_request_lead_days integer NOT NULL DEFAULT 20;

ALTER TABLE platform_operations_settings
    ADD CONSTRAINT ck_platform_operations_settings_lead_days
        CHECK (service_request_lead_days BETWEEN 0 AND 365);
