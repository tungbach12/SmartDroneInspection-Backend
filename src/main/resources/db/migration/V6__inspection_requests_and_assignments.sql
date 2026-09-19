-- WF2 request, quotation, service-order, and Inspector-assignment persistence.
-- Keep this migration forward-only; request files store metadata/object keys only.

CREATE TABLE inspection_requests
(
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id            UUID         NOT NULL REFERENCES organizations (id) ON DELETE RESTRICT,
    asset_id                   UUID         NOT NULL REFERENCES assets (id) ON DELETE RESTRICT,
    schedule_id                UUID                  REFERENCES inspection_schedules (id) ON DELETE RESTRICT,
    checklist_template_id      UUID         NOT NULL REFERENCES checklist_templates (id) ON DELETE RESTRICT,
    request_type               VARCHAR(16)  NOT NULL,
    due_cycle                  DATE,
    linked_maintenance_ticket_id UUID,
    requested_by_user_id       UUID                  REFERENCES users (id) ON DELETE RESTRICT,
    scope                      VARCHAR(4000) NOT NULL,
    priority                   VARCHAR(16)  NOT NULL,
    preferred_deadline         TIMESTAMPTZ,
    site_access_constraints    VARCHAR(2000),
    contact_name               VARCHAR(200),
    contact_phone              VARCHAR(32),
    contact_email              VARCHAR(320),
    status                     VARCHAR(40)  NOT NULL,
    submitted_at               TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_inspection_requests_type CHECK (request_type IN ('PERIODIC', 'AD_HOC')),
    CONSTRAINT ck_inspection_requests_periodic_fields CHECK (
        (request_type = 'PERIODIC' AND schedule_id IS NOT NULL AND due_cycle IS NOT NULL)
        OR (request_type = 'AD_HOC' AND schedule_id IS NULL AND due_cycle IS NULL)
    ),
    CONSTRAINT ck_inspection_requests_scope CHECK (LENGTH(BTRIM(scope)) > 0),
    CONSTRAINT ck_inspection_requests_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_inspection_requests_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'REVISION_REQUIRED', 'QUOTED',
        'AWAITING_CLIENT_APPROVAL', 'ORDER_CONFIRMED', 'ASSIGNMENT_PENDING',
        'READY_FOR_INSPECTION', 'MANUAL_REVIEW', 'CANCELLED'
    ))
);

CREATE INDEX ix_inspection_requests_organization_status
    ON inspection_requests (organization_id, status, created_at DESC);
CREATE INDEX ix_inspection_requests_asset_status
    ON inspection_requests (asset_id, status, created_at DESC);
CREATE INDEX ix_inspection_requests_schedule_due
    ON inspection_requests (schedule_id, due_cycle);
CREATE UNIQUE INDEX uq_inspection_requests_periodic_due_cycle
    ON inspection_requests (asset_id, schedule_id, due_cycle)
    WHERE request_type = 'PERIODIC';

CREATE TABLE inspection_request_attachments
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_request_id  UUID         NOT NULL REFERENCES inspection_requests (id) ON DELETE CASCADE,
    uploaded_by_user_id   UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    file_name             VARCHAR(500) NOT NULL,
    content_type          VARCHAR(160) NOT NULL,
    size_bytes            BIGINT       NOT NULL,
    checksum_sha256       CHAR(64)     NOT NULL,
    object_key             VARCHAR(1000) NOT NULL,
    description            VARCHAR(2000),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inspection_request_attachments_object_key UNIQUE (object_key),
    CONSTRAINT uq_inspection_request_attachments_checksum UNIQUE (inspection_request_id, checksum_sha256),
    CONSTRAINT ck_inspection_request_attachments_file_name CHECK (BTRIM(file_name) <> ''),
    CONSTRAINT ck_inspection_request_attachments_content_type CHECK (BTRIM(content_type) <> ''),
    CONSTRAINT ck_inspection_request_attachments_size CHECK (size_bytes > 0),
    CONSTRAINT ck_inspection_request_attachments_checksum_format CHECK (
        checksum_sha256 = LOWER(checksum_sha256) AND checksum_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_inspection_request_attachments_object_key CHECK (BTRIM(object_key) <> '')
);

CREATE INDEX ix_inspection_request_attachments_request_time
    ON inspection_request_attachments (inspection_request_id, created_at DESC);

CREATE TABLE inspection_quotations
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quotation_series_id      UUID         NOT NULL,
    inspection_request_id    UUID         NOT NULL REFERENCES inspection_requests (id) ON DELETE RESTRICT,
    version_number           INTEGER      NOT NULL,
    previous_version_id      UUID                  REFERENCES inspection_quotations (id) ON DELETE RESTRICT,
    prepared_by_user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    currency                 CHAR(3)      NOT NULL,
    subtotal                 NUMERIC(14,2) NOT NULL,
    tax_amount               NUMERIC(14,2) NOT NULL,
    total_amount             NUMERIC(14,2) NOT NULL,
    pricing_details          JSONB        NOT NULL,
    scope_snapshot           JSONB        NOT NULL,
    estimated_duration_hours NUMERIC(10,2),
    payment_terms            VARCHAR(2000) NOT NULL,
    status                   VARCHAR(32)  NOT NULL,
    sent_at                  TIMESTAMPTZ,
    decided_by_user_id       UUID                  REFERENCES users (id) ON DELETE RESTRICT,
    decided_at               TIMESTAMPTZ,
    revision_reason          VARCHAR(2000),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspection_quotations_series_version UNIQUE (quotation_series_id, version_number),
    CONSTRAINT ck_inspection_quotations_version CHECK (version_number > 0),
    CONSTRAINT ck_inspection_quotations_currency CHECK (
        currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_inspection_quotations_amounts CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0
    ),
    CONSTRAINT ck_inspection_quotations_duration CHECK (
        estimated_duration_hours IS NULL OR estimated_duration_hours > 0
    ),
    CONSTRAINT ck_inspection_quotations_status CHECK (status IN (
        'DRAFT', 'SENT', 'REVISION_REQUESTED', 'APPROVED', 'REJECTED', 'SUPERSEDED'
    )),
    CONSTRAINT ck_inspection_quotations_revision_reason CHECK (
        status <> 'REVISION_REQUESTED' OR (revision_reason IS NOT NULL AND BTRIM(revision_reason) <> '')
    ),
    CONSTRAINT ck_inspection_quotations_decision CHECK (
        status NOT IN ('APPROVED', 'REJECTED')
        OR (decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX ix_inspection_quotations_request_status
    ON inspection_quotations (inspection_request_id, status, created_at DESC);
CREATE INDEX ix_inspection_quotations_series_version
    ON inspection_quotations (quotation_series_id, version_number DESC);

CREATE TABLE inspection_service_orders
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number          VARCHAR(64)  NOT NULL,
    approved_quotation_id UUID         NOT NULL REFERENCES inspection_quotations (id) ON DELETE RESTRICT,
    inspection_request_id UUID         NOT NULL REFERENCES inspection_requests (id) ON DELETE RESTRICT,
    confirmed_by_user_id  UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    confirmed_at          TIMESTAMPTZ  NOT NULL,
    scope_snapshot        JSONB        NOT NULL,
    deliverables          JSONB        NOT NULL,
    payment_terms         VARCHAR(2000) NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspection_service_orders_number UNIQUE (order_number),
    CONSTRAINT uq_inspection_service_orders_quotation UNIQUE (approved_quotation_id),
    CONSTRAINT ck_inspection_service_orders_order_number CHECK (BTRIM(order_number) <> ''),
    CONSTRAINT ck_inspection_service_orders_status CHECK (status IN (
        'CONFIRMED', 'ASSIGNMENT_PENDING', 'READY_FOR_INSPECTION',
        'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ck_inspection_service_orders_completed CHECK (
        status <> 'COMPLETED' OR completed_at IS NOT NULL
    )
);

CREATE INDEX ix_inspection_service_orders_request_status
    ON inspection_service_orders (inspection_request_id, status, created_at DESC);

CREATE TABLE inspection_assignments
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id    UUID         NOT NULL REFERENCES inspection_service_orders (id) ON DELETE RESTRICT,
    inspector_user_id   UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assigned_by_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status              VARCHAR(24)  NOT NULL,
    deadline            TIMESTAMPTZ,
    access_instructions VARCHAR(2000),
    responded_at        TIMESTAMPTZ,
    rejection_reason    VARCHAR(1000),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_inspection_assignments_status CHECK (status IN (
        'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'COMPLETED'
    )),
    CONSTRAINT ck_inspection_assignments_response CHECK (
        status NOT IN ('ACCEPTED', 'REJECTED') OR responded_at IS NOT NULL
    ),
    CONSTRAINT ck_inspection_assignments_rejection CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL AND BTRIM(rejection_reason) <> '')
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    )
);

CREATE INDEX ix_inspection_assignments_inspector_inbox
    ON inspection_assignments (inspector_user_id, status, deadline);
CREATE INDEX ix_inspection_assignments_order_history
    ON inspection_assignments (service_order_id, created_at DESC);
CREATE UNIQUE INDEX uq_inspection_assignments_active_order
    ON inspection_assignments (service_order_id)
    WHERE status IN ('PENDING', 'ACCEPTED');
