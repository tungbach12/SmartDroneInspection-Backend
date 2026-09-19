-- WF4 maintenance assessment, execution, change control, and billing.
-- Cross-workflow foreign keys are added after all V8 tables exist to avoid cycles.

CREATE TABLE maintenance_tickets
(
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id            UUID        NOT NULL REFERENCES organizations (id) ON DELETE RESTRICT,
    asset_id                   UUID        NOT NULL REFERENCES assets (id) ON DELETE RESTRICT,
    accepted_report_version_id UUID        NOT NULL REFERENCES report_versions (id) ON DELETE RESTRICT,
    created_by_user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    priority                   VARCHAR(16) NOT NULL,
    preferred_deadline         TIMESTAMPTZ,
    instructions               VARCHAR(4000),
    status                     VARCHAR(40) NOT NULL,
    resolution_decision        VARCHAR(32),
    released_at                TIMESTAMPTZ,
    closed_at                  TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_maintenance_tickets_priority CHECK (
        priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    ),
    CONSTRAINT ck_maintenance_tickets_status CHECK (status IN (
        'SUBMITTED', 'ASSESSMENT_PENDING', 'ASSESSED', 'QUOTATION_PENDING',
        'AWAITING_CLIENT_APPROVAL', 'ORDER_CONFIRMED', 'EXECUTION_PENDING',
        'IN_PROGRESS', 'CHANGE_PENDING', 'INTERNAL_REVIEW', 'RELEASED',
        'REWORK_REQUESTED', 'REINSPECTION_REQUESTED', 'CLOSED', 'CANCELLED'
    )),
    CONSTRAINT ck_maintenance_tickets_resolution CHECK (
        resolution_decision IS NULL
        OR resolution_decision IN ('ACCEPT_RESOLUTION', 'REQUEST_REWORK', 'REQUEST_REINSPECTION')
    ),
    CONSTRAINT ck_maintenance_tickets_closed CHECK (
        status <> 'CLOSED' OR closed_at IS NOT NULL
    )
);

CREATE INDEX ix_maintenance_tickets_organization_status
    ON maintenance_tickets (organization_id, status, created_at DESC);
CREATE INDEX ix_maintenance_tickets_asset_status
    ON maintenance_tickets (asset_id, status, created_at DESC);

CREATE TABLE maintenance_assignments
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_ticket_id UUID      NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    maintenance_order_id UUID,
    engineer_user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assigned_by_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assignment_type     VARCHAR(16) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    deadline            TIMESTAMPTZ,
    responded_at        TIMESTAMPTZ,
    rejection_reason    VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_maintenance_assignments_type CHECK (
        assignment_type IN ('ASSESSMENT', 'EXECUTION', 'REWORK')
    ),
    CONSTRAINT ck_maintenance_assignments_order_requirement CHECK (
        (assignment_type = 'ASSESSMENT' AND maintenance_order_id IS NULL)
        OR (assignment_type IN ('EXECUTION', 'REWORK') AND maintenance_order_id IS NOT NULL)
    ),
    CONSTRAINT ck_maintenance_assignments_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'COMPLETED')
    ),
    CONSTRAINT ck_maintenance_assignments_response CHECK (
        status NOT IN ('ACCEPTED', 'REJECTED') OR responded_at IS NOT NULL
    ),
    CONSTRAINT ck_maintenance_assignments_rejection CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL AND BTRIM(rejection_reason) <> '')
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    )
);

CREATE INDEX ix_maintenance_assignments_engineer_inbox
    ON maintenance_assignments (engineer_user_id, status, deadline);
CREATE INDEX ix_maintenance_assignments_ticket_history
    ON maintenance_assignments (maintenance_ticket_id, assignment_type, created_at DESC);
CREATE UNIQUE INDEX uq_maintenance_assignments_active
    ON maintenance_assignments (maintenance_ticket_id, assignment_type)
    WHERE status IN ('PENDING', 'ACCEPTED');

CREATE TABLE maintenance_assessments
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_assignment_id UUID        NOT NULL REFERENCES maintenance_assignments (id) ON DELETE RESTRICT,
    maintenance_ticket_id    UUID        NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    engineer_user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assessment_mode          VARCHAR(16) NOT NULL,
    required_work            VARCHAR(4000) NOT NULL,
    materials_estimate       JSONB       NOT NULL,
    labor_hours_estimate     NUMERIC(10,2) NOT NULL,
    duration_hours_estimate  NUMERIC(10,2) NOT NULL,
    risk_notes               VARCHAR(4000),
    assumptions              VARCHAR(4000),
    estimated_cost_min       NUMERIC(14,2) NOT NULL,
    estimated_cost_max       NUMERIC(14,2) NOT NULL,
    currency                 CHAR(3)     NOT NULL,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_maintenance_assessments_assignment UNIQUE (assessment_assignment_id),
    CONSTRAINT ck_maintenance_assessments_mode CHECK (assessment_mode IN ('REMOTE', 'ON_SITE')),
    CONSTRAINT ck_maintenance_assessments_required_work CHECK (BTRIM(required_work) <> ''),
    CONSTRAINT ck_maintenance_assessments_labor CHECK (labor_hours_estimate > 0),
    CONSTRAINT ck_maintenance_assessments_duration CHECK (duration_hours_estimate > 0),
    CONSTRAINT ck_maintenance_assessments_cost CHECK (
        estimated_cost_min >= 0 AND estimated_cost_max >= estimated_cost_min
    ),
    CONSTRAINT ck_maintenance_assessments_currency CHECK (
        currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$'
    )
);

CREATE INDEX ix_maintenance_assessments_ticket
    ON maintenance_assessments (maintenance_ticket_id, completed_at DESC);

CREATE TABLE maintenance_quotations
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quotation_series_id      UUID         NOT NULL,
    maintenance_ticket_id   UUID         NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    maintenance_assessment_id UUID       NOT NULL REFERENCES maintenance_assessments (id) ON DELETE RESTRICT,
    version_number           INTEGER      NOT NULL,
    previous_version_id      UUID,
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
    decided_by_user_id       UUID,
    decided_at               TIMESTAMPTZ,
    revision_reason          VARCHAR(2000),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_maintenance_quotation_previous
        FOREIGN KEY (previous_version_id) REFERENCES maintenance_quotations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_quotation_decider
        FOREIGN KEY (decided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_maintenance_quotations_series_version UNIQUE (quotation_series_id, version_number),
    CONSTRAINT ck_maintenance_quotations_version CHECK (version_number > 0),
    CONSTRAINT ck_maintenance_quotations_currency CHECK (
        currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_maintenance_quotations_amounts CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0
    ),
    CONSTRAINT ck_maintenance_quotations_duration CHECK (
        estimated_duration_hours IS NULL OR estimated_duration_hours > 0
    ),
    CONSTRAINT ck_maintenance_quotations_status CHECK (status IN (
        'DRAFT', 'SENT', 'REVISION_REQUESTED', 'APPROVED', 'REJECTED', 'SUPERSEDED'
    )),
    CONSTRAINT ck_maintenance_quotations_revision_reason CHECK (
        status <> 'REVISION_REQUESTED' OR (revision_reason IS NOT NULL AND BTRIM(revision_reason) <> '')
    ),
    CONSTRAINT ck_maintenance_quotations_decision CHECK (
        status NOT IN ('APPROVED', 'REJECTED')
        OR (decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX ix_maintenance_quotations_ticket_status
    ON maintenance_quotations (maintenance_ticket_id, status, created_at DESC);
CREATE INDEX ix_maintenance_quotations_series_version
    ON maintenance_quotations (quotation_series_id, version_number DESC);

CREATE TABLE maintenance_orders
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_series_id       UUID         NOT NULL,
    order_number          VARCHAR(64)  NOT NULL,
    maintenance_ticket_id UUID         NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    approved_quotation_id UUID,
    change_request_id     UUID,
    version_number        INTEGER      NOT NULL,
    previous_version_id   UUID,
    scope_snapshot        JSONB        NOT NULL,
    approved_amount       NUMERIC(14,2) NOT NULL,
    currency              CHAR(3)      NOT NULL,
    payment_terms         VARCHAR(2000) NOT NULL,
    status                VARCHAR(24)  NOT NULL,
    approved_by_user_id   UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    approved_at           TIMESTAMPTZ  NOT NULL,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_maintenance_order_quotation
        FOREIGN KEY (approved_quotation_id) REFERENCES maintenance_quotations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_order_previous
        FOREIGN KEY (previous_version_id) REFERENCES maintenance_orders (id) ON DELETE RESTRICT,
    CONSTRAINT uq_maintenance_orders_series_version UNIQUE (order_series_id, version_number),
    CONSTRAINT uq_maintenance_orders_number_version UNIQUE (order_number, version_number),
    CONSTRAINT ck_maintenance_orders_number CHECK (BTRIM(order_number) <> ''),
    CONSTRAINT ck_maintenance_orders_version CHECK (version_number > 0),
    CONSTRAINT ck_maintenance_orders_source CHECK (
        (version_number = 1 AND approved_quotation_id IS NOT NULL AND change_request_id IS NULL)
        OR (version_number > 1 AND approved_quotation_id IS NULL AND change_request_id IS NOT NULL)
    ),
    CONSTRAINT ck_maintenance_orders_amount CHECK (approved_amount >= 0),
    CONSTRAINT ck_maintenance_orders_currency CHECK (
        currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_maintenance_orders_status CHECK (
        status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'SUPERSEDED', 'CANCELLED')
    ),
    CONSTRAINT ck_maintenance_orders_completed CHECK (
        status <> 'COMPLETED' OR completed_at IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_maintenance_orders_approved_quotation
    ON maintenance_orders (approved_quotation_id)
    WHERE approved_quotation_id IS NOT NULL;
CREATE INDEX ix_maintenance_orders_ticket_status
    ON maintenance_orders (maintenance_ticket_id, status, created_at DESC);

CREATE TABLE maintenance_work_logs
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_ticket_id   UUID         NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    execution_assignment_id UUID        NOT NULL REFERENCES maintenance_assignments (id) ON DELETE RESTRICT,
    engineer_user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    started_at              TIMESTAMPTZ  NOT NULL,
    ended_at                TIMESTAMPTZ,
    progress_percent        NUMERIC(5,2) NOT NULL DEFAULT 0,
    work_summary            VARCHAR(4000) NOT NULL,
    materials_used          JSONB        NOT NULL,
    labor_hours             NUMERIC(10,2) NOT NULL,
    actual_cost             NUMERIC(14,2),
    currency                CHAR(3),
    status                  VARCHAR(24)  NOT NULL,
    submitted_at            TIMESTAMPTZ,
    verified_by_user_id     UUID,
    verified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_work_log_verifier
        FOREIGN KEY (verified_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_work_logs_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_work_logs_time_order CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT ck_work_logs_summary CHECK (BTRIM(work_summary) <> ''),
    CONSTRAINT ck_work_logs_labor CHECK (labor_hours >= 0),
    CONSTRAINT ck_work_logs_cost_currency CHECK (
        (actual_cost IS NULL AND currency IS NULL)
        OR (actual_cost IS NOT NULL AND actual_cost >= 0 AND currency IS NOT NULL
            AND currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_work_logs_status CHECK (
        status IN ('IN_PROGRESS', 'PAUSED_FOR_CHANGE', 'SUBMITTED', 'VERIFIED')
    ),
    CONSTRAINT ck_work_logs_verification CHECK (
        (status = 'VERIFIED' AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL)
        OR (status <> 'VERIFIED' AND verified_by_user_id IS NULL AND verified_at IS NULL)
    )
);

CREATE INDEX ix_maintenance_work_logs_ticket_time
    ON maintenance_work_logs (maintenance_ticket_id, started_at DESC);
CREATE INDEX ix_maintenance_work_logs_engineer_status
    ON maintenance_work_logs (engineer_user_id, status, started_at DESC);

CREATE TABLE maintenance_change_requests
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_ticket_id UUID        NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    work_log_id          UUID        NOT NULL REFERENCES maintenance_work_logs (id) ON DELETE RESTRICT,
    current_order_id     UUID        NOT NULL,
    requested_by_user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    reason               VARCHAR(2000) NOT NULL,
    additional_scope     VARCHAR(4000) NOT NULL,
    estimated_cost_delta NUMERIC(14,2),
    currency             CHAR(3),
    status               VARCHAR(24) NOT NULL,
    decided_by_user_id   UUID,
    decided_at           TIMESTAMPTZ,
    decision_reason      VARCHAR(2000),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_change_request_decider
        FOREIGN KEY (decided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_change_requests_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_change_requests_scope CHECK (BTRIM(additional_scope) <> ''),
    CONSTRAINT ck_change_requests_cost_currency CHECK (
        (estimated_cost_delta IS NULL AND currency IS NULL)
        OR (estimated_cost_delta IS NOT NULL AND estimated_cost_delta >= 0 AND currency IS NOT NULL
            AND currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_change_requests_status CHECK (
        status IN ('SUBMITTED', 'QUOTED', 'APPROVED', 'REJECTED', 'IMPLEMENTED')
    ),
    CONSTRAINT ck_change_requests_decision CHECK (
        status IN ('SUBMITTED', 'QUOTED')
        OR (decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX ix_change_requests_ticket_status
    ON maintenance_change_requests (maintenance_ticket_id, status, created_at DESC);

CREATE TABLE maintenance_ticket_findings
(
    maintenance_ticket_id UUID NOT NULL REFERENCES maintenance_tickets (id) ON DELETE CASCADE,
    verified_finding_id    UUID NOT NULL REFERENCES verified_findings (id) ON DELETE RESTRICT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (maintenance_ticket_id, verified_finding_id)
);

CREATE INDEX ix_ticket_findings_finding
    ON maintenance_ticket_findings (verified_finding_id);

CREATE TABLE invoices
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number       VARCHAR(64) NOT NULL,
    organization_id      UUID        NOT NULL REFERENCES organizations (id) ON DELETE RESTRICT,
    maintenance_order_id UUID        NOT NULL REFERENCES maintenance_orders (id) ON DELETE RESTRICT,
    maintenance_ticket_id UUID      NOT NULL REFERENCES maintenance_tickets (id) ON DELETE RESTRICT,
    currency             CHAR(3)    NOT NULL,
    subtotal             NUMERIC(14,2) NOT NULL,
    tax_amount           NUMERIC(14,2) NOT NULL,
    total_amount         NUMERIC(14,2) NOT NULL,
    status               VARCHAR(24) NOT NULL,
    issued_at            TIMESTAMPTZ,
    due_at               TIMESTAMPTZ,
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_invoices_number CHECK (BTRIM(invoice_number) <> ''),
    CONSTRAINT ck_invoices_currency CHECK (
        currency = UPPER(currency) AND BTRIM(currency) ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_invoices_amounts CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0
    ),
    CONSTRAINT ck_invoices_status CHECK (
        status IN ('DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'VOID')
    ),
    CONSTRAINT ck_invoices_issue_due CHECK (
        due_at IS NULL OR issued_at IS NULL OR due_at >= issued_at
    ),
    CONSTRAINT ck_invoices_paid CHECK (
        status <> 'PAID' OR paid_at IS NOT NULL
    )
);

CREATE INDEX ix_invoices_organization_status
    ON invoices (organization_id, status, created_at DESC);
CREATE INDEX ix_invoices_ticket
    ON invoices (maintenance_ticket_id);

ALTER TABLE maintenance_assignments
    ADD CONSTRAINT fk_maintenance_assignment_order
    FOREIGN KEY (maintenance_order_id) REFERENCES maintenance_orders (id) ON DELETE RESTRICT;

ALTER TABLE maintenance_orders
    ADD CONSTRAINT fk_maintenance_order_change_request
    FOREIGN KEY (change_request_id) REFERENCES maintenance_change_requests (id) ON DELETE RESTRICT;

ALTER TABLE maintenance_change_requests
    ADD CONSTRAINT fk_change_request_current_order
    FOREIGN KEY (current_order_id) REFERENCES maintenance_orders (id) ON DELETE RESTRICT;

ALTER TABLE evidence
    ADD CONSTRAINT fk_evidence_work_log
    FOREIGN KEY (maintenance_work_log_id) REFERENCES maintenance_work_logs (id) ON DELETE RESTRICT;
