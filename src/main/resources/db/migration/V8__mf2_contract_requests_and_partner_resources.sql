CREATE TABLE contract_partner_inspectors (
    inspector_user_id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    status character varying(16) NOT NULL DEFAULT 'ACTIVE',
    registered_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_contract_partner_inspectors_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_contract_partner_inspectors_inspector FOREIGN KEY (inspector_user_id, organization_id) REFERENCES users(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_partner_inspectors_registrar FOREIGN KEY (registered_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE contract_partner_drones (
    drone_id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    status character varying(16) NOT NULL DEFAULT 'ACTIVE',
    registered_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_contract_partner_drones_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_contract_partner_drones_drone FOREIGN KEY (drone_id, organization_id) REFERENCES drones(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_partner_drones_registrar FOREIGN KEY (registered_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX ix_contract_partner_inspectors_org_status ON contract_partner_inspectors (organization_id, status);
CREATE INDEX ix_contract_partner_drones_org_status ON contract_partner_drones (organization_id, status);

CREATE TABLE service_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    description character varying(4000) NOT NULL,
    requested_from date NOT NULL,
    requested_until date NOT NULL,
    timezone character varying(64) NOT NULL,
    window_start time without time zone NOT NULL DEFAULT '08:00',
    window_end time without time zone NOT NULL DEFAULT '17:00',
    request_type character varying(16) NOT NULL,
    recurrence_unit character varying(16),
    recurrence_interval integer,
    occurrence_count integer,
    recurrence_end_date date,
    status character varying(24) NOT NULL DEFAULT 'OPEN',
    accepted_quote_version integer,
    accepted_by_user_id uuid,
    accepted_at timestamp with time zone,
    contract_object_key character varying(1000),
    contract_file_name character varying(500),
    contract_content_type character varying(160),
    contract_size_bytes bigint,
    contract_checksum_sha256 character(64),
    contract_uploaded_by_user_id uuid,
    contract_uploaded_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_requests_date_range CHECK (requested_until >= requested_from),
    CONSTRAINT ck_service_requests_time_window CHECK (window_end > window_start),
    CONSTRAINT ck_service_requests_type CHECK (request_type IN ('ONE_TIME', 'RECURRING')),
    CONSTRAINT ck_service_requests_recurrence CHECK (
        (request_type = 'ONE_TIME' AND recurrence_unit IS NULL AND recurrence_interval IS NULL AND occurrence_count IS NULL AND recurrence_end_date IS NULL)
        OR (request_type = 'RECURRING' AND recurrence_unit IN ('DAY', 'WEEK', 'MONTH') AND recurrence_interval BETWEEN 1 AND 52 AND ((occurrence_count BETWEEN 1 AND 100) <> (recurrence_end_date IS NOT NULL)) AND (recurrence_end_date IS NULL OR recurrence_end_date >= requested_from))
    ),
    CONSTRAINT ck_service_requests_status CHECK (status IN ('OPEN', 'QUOTED', 'ACCEPTED', 'CONTRACT_SIGNED', 'ASSIGNED', 'CANCELLED')),
    CONSTRAINT fk_service_requests_asset_tenant FOREIGN KEY (asset_id, organization_id) REFERENCES assets(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_requests_creator_tenant FOREIGN KEY (created_by_user_id, organization_id) REFERENCES users(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_requests_acceptor FOREIGN KEY (accepted_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_requests_contract_uploader FOREIGN KEY (contract_uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_requests_acceptance CHECK ((accepted_quote_version IS NULL AND accepted_by_user_id IS NULL AND accepted_at IS NULL) OR (accepted_quote_version IS NOT NULL AND accepted_by_user_id IS NOT NULL AND accepted_at IS NOT NULL)),
    CONSTRAINT ck_service_requests_contract_scan CHECK ((contract_object_key IS NULL AND contract_file_name IS NULL AND contract_content_type IS NULL AND contract_size_bytes IS NULL AND contract_checksum_sha256 IS NULL AND contract_uploaded_by_user_id IS NULL AND contract_uploaded_at IS NULL) OR (contract_object_key IS NOT NULL AND contract_file_name IS NOT NULL AND contract_content_type IN ('application/pdf', 'image/jpeg', 'image/png') AND contract_size_bytes > 0 AND contract_checksum_sha256 IS NOT NULL AND contract_uploaded_by_user_id IS NOT NULL AND contract_uploaded_at IS NOT NULL))
);

CREATE INDEX ix_service_requests_org_created ON service_requests (organization_id, created_at DESC);
CREATE INDEX ix_service_requests_status_created ON service_requests (status, created_at DESC);

CREATE TABLE service_request_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL,
    author_user_id uuid NOT NULL,
    body character varying(4000) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_service_request_messages_body CHECK (length(btrim(body)) > 0),
    CONSTRAINT fk_service_request_messages_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_request_messages_author FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX ix_service_request_messages_request_created ON service_request_messages (request_id, created_at, id);

CREATE TABLE service_request_quotes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL,
    version_no integer NOT NULL,
    scope_snapshot character varying(4000) NOT NULL,
    first_occurrence_start_at timestamp with time zone NOT NULL,
    first_occurrence_end_at timestamp with time zone NOT NULL,
    inspector_id uuid NOT NULL,
    drone_id uuid NOT NULL,
    price_per_occurrence_usd numeric(12,2) NOT NULL,
    issued_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_request_quotes_version UNIQUE (request_id, version_no),
    CONSTRAINT uq_service_request_quotes_id_request UNIQUE (id, request_id),
    CONSTRAINT ck_service_request_quotes_version CHECK (version_no > 0),
    CONSTRAINT ck_service_request_quotes_time CHECK (first_occurrence_end_at > first_occurrence_start_at),
    CONSTRAINT ck_service_request_quotes_price CHECK (price_per_occurrence_usd > 0),
    CONSTRAINT fk_service_request_quotes_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_request_quotes_inspector FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_request_quotes_drone FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_request_quotes_issuer FOREIGN KEY (issued_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX ix_service_request_quotes_latest ON service_request_quotes (request_id, version_no DESC);

ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_accepted_quote FOREIGN KEY (id, accepted_quote_version) REFERENCES service_request_quotes(request_id, version_no) ON DELETE RESTRICT;

CREATE TABLE service_request_occurrences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL,
    occurrence_index integer NOT NULL,
    planned_start_at timestamp with time zone NOT NULL,
    planned_end_at timestamp with time zone NOT NULL,
    status character varying(24) NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
    inspector_id uuid,
    drone_id uuid,
    inspection_id uuid UNIQUE,
    assigned_by_user_id uuid,
    assigned_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_request_occurrences_index UNIQUE (request_id, occurrence_index),
    CONSTRAINT ck_service_request_occurrences_index CHECK (occurrence_index > 0),
    CONSTRAINT ck_service_request_occurrences_time CHECK (planned_end_at > planned_start_at),
    CONSTRAINT ck_service_request_occurrences_status CHECK (status IN ('PENDING_ASSIGNMENT', 'ASSIGNED', 'CANCELLED')),
    CONSTRAINT ck_service_request_occurrences_assignment CHECK ((status = 'PENDING_ASSIGNMENT' AND inspector_id IS NULL AND drone_id IS NULL AND inspection_id IS NULL AND assigned_by_user_id IS NULL AND assigned_at IS NULL) OR (status = 'ASSIGNED' AND inspector_id IS NOT NULL AND drone_id IS NOT NULL AND inspection_id IS NOT NULL AND assigned_by_user_id IS NOT NULL AND assigned_at IS NOT NULL) OR status = 'CANCELLED'),
    CONSTRAINT fk_service_request_occurrences_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_request_occurrences_inspector FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_request_occurrences_drone FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_request_occurrences_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_request_occurrences_assigner FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX ix_service_request_occurrences_request_status ON service_request_occurrences (request_id, status, occurrence_index);

-- MF2 partner Inspectors and drones are validated by the contracts assignment boundary. The existing
-- one-off/scheduled API still enforces same-organization scope in its service before inserting.
ALTER TABLE inspections DROP CONSTRAINT fk_inspections_inspector_tenant;
ALTER TABLE inspections DROP CONSTRAINT fk_inspections_drone_tenant;
ALTER TABLE inspections
    ADD COLUMN partner_assignment boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_inspections_partner_drone CHECK (NOT partner_assignment OR (inspector_id IS NOT NULL AND drone_id IS NOT NULL));
