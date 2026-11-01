-- Migration V17: Simplify MF2 database schema
-- 1. Drop redundant contract_partner_inspectors and contract_partner_drones tables (query MF1 users and drones directly).
-- 2. Simplify service_requests by dropping unused acceptance actor/timestamp and redundant contract file metadata columns.
-- 3. Simplify service_request_quotes by standardizing on price_per_occurrence_vnd and dropping legacy USD/currency columns.
-- 4. Simplify service_request_occurrences by dropping duplicate per-occurrence contract columns, formula-derived finance columns,
--    and write-only audit/report/payment columns (reducing from 42 columns to 20 columns).

DROP TABLE IF EXISTS contract_partner_drones;
DROP TABLE IF EXISTS contract_partner_inspectors;

ALTER TABLE service_requests
    DROP CONSTRAINT IF EXISTS ck_service_requests_acceptance,
    DROP CONSTRAINT IF EXISTS fk_service_requests_acceptor,
    DROP CONSTRAINT IF EXISTS ck_service_requests_contract_scan,
    DROP CONSTRAINT IF EXISTS fk_service_requests_contract_uploader,
    DROP COLUMN IF EXISTS accepted_by_user_id,
    DROP COLUMN IF EXISTS accepted_at,
    DROP COLUMN IF EXISTS contract_file_name,
    DROP COLUMN IF EXISTS contract_content_type,
    DROP COLUMN IF EXISTS contract_size_bytes,
    DROP COLUMN IF EXISTS contract_checksum_sha256,
    DROP COLUMN IF EXISTS contract_uploaded_by_user_id,
    ADD CONSTRAINT ck_service_requests_contract_scan CHECK (
        (contract_object_key IS NULL AND contract_uploaded_at IS NULL)
        OR (contract_object_key IS NOT NULL AND contract_uploaded_at IS NOT NULL)
    );

UPDATE service_request_quotes
SET price_per_occurrence_vnd = COALESCE(price_per_occurrence_vnd, price_per_occurrence_usd)
WHERE price_per_occurrence_vnd IS NULL;

ALTER TABLE service_request_quotes
    DROP CONSTRAINT IF EXISTS ck_service_request_quotes_price,
    DROP COLUMN IF EXISTS price_per_occurrence_usd,
    DROP COLUMN IF EXISTS currency,
    ALTER COLUMN price_per_occurrence_vnd SET NOT NULL,
    ADD CONSTRAINT ck_service_request_quotes_price CHECK (price_per_occurrence_vnd > 0);

ALTER TABLE service_request_occurrences
    DROP CONSTRAINT IF EXISTS ck_service_request_occurrences_assignment,
    DROP CONSTRAINT IF EXISTS fk_service_request_occurrences_assigner,
    DROP CONSTRAINT IF EXISTS fk_service_request_occurrences_contract_uploader,
    DROP CONSTRAINT IF EXISTS fk_service_request_occurrences_report_acceptor,
    DROP CONSTRAINT IF EXISTS fk_service_request_occurrences_report_version,
    DROP CONSTRAINT IF EXISTS fk_service_request_occurrences_payment_confirmer,
    DROP CONSTRAINT IF EXISTS fk_service_request_occurrences_payout_recorder,
    DROP COLUMN IF EXISTS assigned_by_user_id,
    DROP COLUMN IF EXISTS assigned_at,
    DROP COLUMN IF EXISTS contract_object_key,
    DROP COLUMN IF EXISTS contract_file_name,
    DROP COLUMN IF EXISTS contract_content_type,
    DROP COLUMN IF EXISTS contract_size_bytes,
    DROP COLUMN IF EXISTS contract_checksum_sha256,
    DROP COLUMN IF EXISTS contract_uploaded_by_user_id,
    DROP COLUMN IF EXISTS contract_uploaded_at,
    DROP COLUMN IF EXISTS currency,
    DROP COLUMN IF EXISTS service_base_amount,
    DROP COLUMN IF EXISTS vat_amount,
    DROP COLUMN IF EXISTS commission_amount,
    DROP COLUMN IF EXISTS inspector_gross_payout,
    DROP COLUMN IF EXISTS tncn_amount,
    DROP COLUMN IF EXISTS inspector_net_payout,
    DROP COLUMN IF EXISTS report_accepted_by_user_id,
    DROP COLUMN IF EXISTS accepted_report_version_id,
    DROP COLUMN IF EXISTS payment_amount,
    DROP COLUMN IF EXISTS payment_confirmed_by_user_id,
    DROP COLUMN IF EXISTS payos_payment_link_id,
    DROP COLUMN IF EXISTS payout_recorded_by_user_id,
    ADD CONSTRAINT ck_service_request_occurrences_assignment CHECK (
        (status = 'PENDING_ASSIGNMENT' AND inspector_id IS NULL AND drone_id IS NULL AND inspection_id IS NULL)
        OR (status = 'ASSIGNED' AND inspector_id IS NOT NULL AND drone_id IS NOT NULL AND inspection_id IS NOT NULL)
        OR status = 'CANCELLED'
    );
