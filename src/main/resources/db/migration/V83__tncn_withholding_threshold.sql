ALTER TABLE inspection_finance_settings
    ADD COLUMN tncn_threshold_amount numeric(15, 2) NOT NULL DEFAULT 2000000.00;

ALTER TABLE inspection_finance_settings
    ADD CONSTRAINT ck_inspection_finance_settings_tncn_threshold
        CHECK (tncn_threshold_amount >= 0);

ALTER TABLE service_invoices
    ADD COLUMN tncn_threshold_amount numeric(15, 2);

ALTER TABLE service_invoices
    ADD CONSTRAINT ck_service_invoices_tncn_threshold
        CHECK (tncn_threshold_amount IS NULL OR tncn_threshold_amount >= 0);
