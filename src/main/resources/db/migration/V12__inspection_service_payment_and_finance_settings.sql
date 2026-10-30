-- Migration V12: Inspection service payment, finance settings, and occurrence contracts
-- Supports per-occurrence signed contracts, VND quote pricing, VAT split, platform commission,
-- PIT withholding (MVP assumption), customer report acceptance, and manager payment/payout confirmation.

CREATE TABLE inspection_finance_settings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    commission_rate numeric(5,2),
    vat_rate numeric(5,2),
    tncn_rate numeric(5,2),
    updated_by_user_id uuid,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inspection_finance_settings_commission CHECK (commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 100)),
    CONSTRAINT ck_inspection_finance_settings_vat CHECK (vat_rate IS NULL OR (vat_rate >= 0 AND vat_rate <= 100)),
    CONSTRAINT ck_inspection_finance_settings_tncn CHECK (tncn_rate IS NULL OR (tncn_rate >= 0 AND tncn_rate <= 100)),
    CONSTRAINT fk_inspection_finance_settings_user FOREIGN KEY (updated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Service request quotes: support explicit currency and VND pricing alongside legacy USD
ALTER TABLE service_request_quotes
    ADD COLUMN currency character varying(3) NOT NULL DEFAULT 'VND',
    ADD COLUMN price_per_occurrence_vnd numeric(15, 2);

ALTER TABLE service_request_quotes
    ALTER COLUMN price_per_occurrence_usd DROP NOT NULL;

ALTER TABLE service_request_quotes
    DROP CONSTRAINT IF EXISTS ck_service_request_quotes_price;

ALTER TABLE service_request_quotes
    ADD CONSTRAINT ck_service_request_quotes_price CHECK (
        (currency = 'USD' AND price_per_occurrence_usd IS NOT NULL AND price_per_occurrence_usd > 0)
        OR (currency = 'VND' AND price_per_occurrence_vnd IS NOT NULL AND price_per_occurrence_vnd > 0)
    );

UPDATE service_request_quotes
    SET currency = 'USD'
    WHERE price_per_occurrence_usd IS NOT NULL AND price_per_occurrence_vnd IS NULL;

-- Support DOCX signed contracts on service_requests alongside PDF, JPEG, and PNG
ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS ck_service_requests_contract_scan;
ALTER TABLE service_requests ADD CONSTRAINT ck_service_requests_contract_scan CHECK (
    (contract_object_key IS NULL AND contract_file_name IS NULL AND contract_content_type IS NULL AND contract_size_bytes IS NULL AND contract_checksum_sha256 IS NULL AND contract_uploaded_by_user_id IS NULL AND contract_uploaded_at IS NULL)
    OR (contract_object_key IS NOT NULL AND contract_file_name IS NOT NULL AND contract_content_type IN ('application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'image/jpeg', 'image/png') AND contract_size_bytes > 0 AND contract_checksum_sha256 IS NOT NULL AND contract_uploaded_by_user_id IS NOT NULL AND contract_uploaded_at IS NOT NULL)
);

-- Service request occurrences: per-occurrence contract attachment, finance snapshot, acceptance, and payment/payout states
ALTER TABLE service_request_occurrences
    ADD COLUMN contract_object_key character varying(1000),
    ADD COLUMN contract_file_name character varying(500),
    ADD COLUMN contract_content_type character varying(160),
    ADD COLUMN contract_size_bytes bigint,
    ADD COLUMN contract_checksum_sha256 character varying(64),
    ADD COLUMN contract_uploaded_by_user_id uuid,
    ADD COLUMN contract_uploaded_at timestamp with time zone,

    ADD COLUMN currency character varying(3) NOT NULL DEFAULT 'VND',
    ADD COLUMN total_price numeric(15, 2),
    ADD COLUMN service_base_amount numeric(15, 2),
    ADD COLUMN vat_rate numeric(5, 2),
    ADD COLUMN vat_amount numeric(15, 2),
    ADD COLUMN commission_rate numeric(5, 2),
    ADD COLUMN commission_amount numeric(15, 2),

    ADD COLUMN inspector_gross_payout numeric(15, 2),
    ADD COLUMN tncn_rate numeric(5, 2),
    ADD COLUMN tncn_amount numeric(15, 2),
    ADD COLUMN inspector_net_payout numeric(15, 2),

    ADD COLUMN report_accepted_at timestamp with time zone,
    ADD COLUMN report_accepted_by_user_id uuid,
    ADD COLUMN accepted_report_version_id uuid,

    ADD COLUMN payment_status character varying(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN payment_reference character varying(255),
    ADD COLUMN payment_amount numeric(15, 2),
    ADD COLUMN payment_confirmed_by_user_id uuid,
    ADD COLUMN payment_confirmed_at timestamp with time zone,

    ADD COLUMN payout_status character varying(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN payout_reference character varying(255),
    ADD COLUMN payout_recorded_by_user_id uuid,
    ADD COLUMN payout_recorded_at timestamp with time zone;

ALTER TABLE service_request_occurrences
    ADD CONSTRAINT fk_service_request_occurrences_contract_uploader
        FOREIGN KEY (contract_uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_service_request_occurrences_report_acceptor
        FOREIGN KEY (report_accepted_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_service_request_occurrences_report_version
        FOREIGN KEY (accepted_report_version_id) REFERENCES inspection_reports(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_service_request_occurrences_payment_confirmer
        FOREIGN KEY (payment_confirmed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_service_request_occurrences_payout_recorder
        FOREIGN KEY (payout_recorded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_service_request_occurrences_payment_status
        CHECK (payment_status IN ('PENDING', 'PAYMENT_DUE', 'PAID')),
    ADD CONSTRAINT ck_service_request_occurrences_payout_status
        CHECK (payout_status IN ('PENDING', 'ELIGIBLE', 'PAID'));

CREATE INDEX ix_service_request_occurrences_payment_status ON service_request_occurrences (payment_status);
CREATE INDEX ix_service_request_occurrences_payout_status ON service_request_occurrences (payout_status);
