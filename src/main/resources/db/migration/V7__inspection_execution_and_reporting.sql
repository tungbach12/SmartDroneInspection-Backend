-- WF3 inspection execution, evidence, findings, and report history.
-- Evidence stores metadata and MinIO object keys only; file bytes stay in object storage.

CREATE TABLE inspections
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id      UUID        NOT NULL REFERENCES inspection_service_orders (id) ON DELETE RESTRICT,
    accepted_assignment_id UUID      NOT NULL REFERENCES inspection_assignments (id) ON DELETE RESTRICT,
    asset_id              UUID        NOT NULL REFERENCES assets (id) ON DELETE RESTRICT,
    author_user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    checklist_template_id UUID        NOT NULL REFERENCES checklist_templates (id) ON DELETE RESTRICT,
    status                VARCHAR(32) NOT NULL,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspections_service_order UNIQUE (service_order_id),
    CONSTRAINT uq_inspections_assignment UNIQUE (accepted_assignment_id),
    CONSTRAINT ck_inspections_status CHECK (status IN (
        'READY_FOR_INSPECTION', 'IN_PROGRESS', 'AWAITING_AI_REVIEW',
        'AWAITING_REPORT', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ck_inspections_completed CHECK (
        status <> 'COMPLETED' OR completed_at IS NOT NULL
    ),
    CONSTRAINT ck_inspections_time_order CHECK (
        completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at
    )
);

CREATE INDEX ix_inspections_author_status
    ON inspections (author_user_id, status, updated_at DESC);
CREATE INDEX ix_inspections_asset_status
    ON inspections (asset_id, status, updated_at DESC);

CREATE TABLE checklist_responses
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id       UUID        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    checklist_item_id   UUID        NOT NULL REFERENCES checklist_items (id) ON DELETE RESTRICT,
    response_value      JSONB       NOT NULL,
    notes               VARCHAR(4000),
    completed_by_user_id UUID       NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_checklist_responses_item UNIQUE (inspection_id, checklist_item_id)
);

CREATE INDEX ix_checklist_responses_inspection
    ON checklist_responses (inspection_id, completed_at);

CREATE TABLE evidence
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id          UUID,
    maintenance_work_log_id UUID,
    uploaded_by_user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    evidence_kind          VARCHAR(32) NOT NULL,
    file_name              VARCHAR(500) NOT NULL,
    content_type           VARCHAR(160) NOT NULL,
    size_bytes             BIGINT      NOT NULL,
    checksum_sha256        CHAR(64)    NOT NULL,
    object_key             VARCHAR(1000) NOT NULL,
    capture_time           TIMESTAMPTZ,
    source                 VARCHAR(32) NOT NULL,
    latitude               NUMERIC(9,6),
    longitude              NUMERIC(9,6),
    external_reference     VARCHAR(200),
    upload_status          VARCHAR(24) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE RESTRICT,
    CONSTRAINT uq_evidence_object_key UNIQUE (object_key),
    CONSTRAINT ck_evidence_parent CHECK (
        (inspection_id IS NOT NULL AND maintenance_work_log_id IS NULL)
        OR (inspection_id IS NULL AND maintenance_work_log_id IS NOT NULL)
    ),
    CONSTRAINT ck_evidence_kind CHECK (
        evidence_kind IN ('INSPECTION', 'BEFORE_MAINTENANCE', 'AFTER_MAINTENANCE', 'OTHER')
    ),
    CONSTRAINT ck_evidence_file_name CHECK (BTRIM(file_name) <> ''),
    CONSTRAINT ck_evidence_content_type CHECK (BTRIM(content_type) <> ''),
    CONSTRAINT ck_evidence_size CHECK (size_bytes > 0),
    CONSTRAINT ck_evidence_checksum_format CHECK (
        checksum_sha256 = LOWER(checksum_sha256) AND checksum_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_evidence_object_key CHECK (BTRIM(object_key) <> ''),
    CONSTRAINT ck_evidence_source CHECK (
        source IN ('SD_CARD', 'WEB_UPLOAD', 'MOBILE_UPLOAD', 'IMPORTED')
    ),
    CONSTRAINT ck_evidence_upload_status CHECK (
        upload_status IN ('UPLOADING', 'AVAILABLE', 'FAILED', 'QUARANTINED')
    ),
    CONSTRAINT ck_evidence_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_evidence_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX ix_evidence_inspection_time
    ON evidence (inspection_id, created_at DESC)
    WHERE inspection_id IS NOT NULL;
CREATE INDEX ix_evidence_work_log_time
    ON evidence (maintenance_work_log_id, created_at DESC)
    WHERE maintenance_work_log_id IS NOT NULL;
CREATE UNIQUE INDEX uq_evidence_inspection_checksum
    ON evidence (inspection_id, checksum_sha256)
    WHERE inspection_id IS NOT NULL;
CREATE UNIQUE INDEX uq_evidence_work_log_checksum
    ON evidence (maintenance_work_log_id, checksum_sha256)
    WHERE maintenance_work_log_id IS NOT NULL;

CREATE TABLE ai_finding_candidates
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id        UUID        NOT NULL REFERENCES evidence (id) ON DELETE RESTRICT,
    model_name         VARCHAR(160) NOT NULL,
    model_version      VARCHAR(80)  NOT NULL,
    predicted_label    VARCHAR(160) NOT NULL,
    confidence         NUMERIC(6,5) NOT NULL,
    bounding_box       JSONB       NOT NULL,
    status             VARCHAR(24) NOT NULL,
    reviewed_by_user_id UUID,
    reviewed_at        TIMESTAMPTZ,
    rejection_reason   VARCHAR(2000),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_candidate_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_candidate_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_ai_candidate_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'MODIFIED', 'REJECTED')
    ),
    CONSTRAINT ck_ai_candidate_review_metadata CHECK (
        (status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (status IN ('CONFIRMED', 'MODIFIED') AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND rejection_reason IS NOT NULL AND BTRIM(rejection_reason) <> '')
    )
);

CREATE INDEX ix_ai_candidates_evidence_status
    ON ai_finding_candidates (evidence_id, status, created_at DESC);

CREATE TABLE verified_findings
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id      UUID        NOT NULL REFERENCES inspections (id) ON DELETE RESTRICT,
    evidence_id        UUID,
    ai_candidate_id    UUID,
    created_by_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    source             VARCHAR(24) NOT NULL,
    finding_code       VARCHAR(64) NOT NULL,
    defect_label       VARCHAR(160) NOT NULL,
    severity           VARCHAR(16) NOT NULL,
    location_description VARCHAR(1000) NOT NULL,
    bounding_box       JSONB,
    technical_notes    VARCHAR(4000) NOT NULL,
    recommended_action VARCHAR(4000),
    status             VARCHAR(24) NOT NULL,
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_verified_finding_evidence
        FOREIGN KEY (evidence_id) REFERENCES evidence (id) ON DELETE RESTRICT,
    CONSTRAINT fk_verified_finding_candidate
        FOREIGN KEY (ai_candidate_id) REFERENCES ai_finding_candidates (id) ON DELETE RESTRICT,
    CONSTRAINT uq_verified_findings_code UNIQUE (inspection_id, finding_code),
    CONSTRAINT ck_verified_finding_source CHECK (
        source IN ('AI_CONFIRMED', 'AI_MODIFIED', 'MANUAL')
    ),
    CONSTRAINT ck_verified_finding_source_reference CHECK (
        (source = 'MANUAL' AND ai_candidate_id IS NULL)
        OR (source IN ('AI_CONFIRMED', 'AI_MODIFIED') AND ai_candidate_id IS NOT NULL)
    ),
    CONSTRAINT ck_verified_finding_severity CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_verified_finding_status CHECK (
        status IN ('OPEN', 'IN_MAINTENANCE', 'RESOLVED')
    ),
    CONSTRAINT ck_verified_finding_resolution CHECK (
        (status = 'RESOLVED' AND resolved_at IS NOT NULL)
        OR (status <> 'RESOLVED' AND resolved_at IS NULL)
    )
);

CREATE INDEX ix_verified_findings_inspection_status
    ON verified_findings (inspection_id, status, severity);

CREATE TABLE inspection_reports
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id         UUID        NOT NULL REFERENCES inspections (id) ON DELETE RESTRICT,
    author_user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status                VARCHAR(32) NOT NULL,
    current_version_number INTEGER    NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspection_reports_inspection UNIQUE (inspection_id),
    CONSTRAINT ck_inspection_reports_status CHECK (status IN (
        'DRAFT', 'AWAITING_PEER_REVIEW', 'CHANGES_REQUESTED',
        'TECHNICALLY_APPROVED', 'RELEASED', 'REVISION_REQUESTED', 'ACCEPTED'
    )),
    CONSTRAINT ck_inspection_reports_version CHECK (current_version_number >= 0)
);

CREATE INDEX ix_inspection_reports_author_status
    ON inspection_reports (author_user_id, status, updated_at DESC);

CREATE TABLE report_versions
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id             UUID        NOT NULL REFERENCES inspection_reports (id) ON DELETE CASCADE,
    version_number        INTEGER     NOT NULL,
    source_version_id     UUID,
    created_by_user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    content_snapshot      JSONB       NOT NULL,
    pdf_object_key        VARCHAR(1000),
    status                VARCHAR(32) NOT NULL,
    submitted_at          TIMESTAMPTZ,
    technically_approved_at TIMESTAMPTZ,
    released_at           TIMESTAMPTZ,
    accepted_at           TIMESTAMPTZ,
    immutable              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_version_source
        FOREIGN KEY (source_version_id) REFERENCES report_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_report_versions_number UNIQUE (report_id, version_number),
    CONSTRAINT ck_report_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_report_versions_status CHECK (status IN (
        'DRAFT', 'AWAITING_PEER_REVIEW', 'CHANGES_REQUESTED',
        'TECHNICALLY_APPROVED', 'RELEASED', 'REVISION_REQUESTED', 'ACCEPTED'
    )),
    CONSTRAINT ck_report_versions_acceptance CHECK (
        (status = 'ACCEPTED' AND immutable = TRUE AND accepted_at IS NOT NULL)
        OR (status <> 'ACCEPTED' AND immutable = FALSE)
    ),
    CONSTRAINT ck_report_versions_pdf_key CHECK (
        pdf_object_key IS NULL OR BTRIM(pdf_object_key) <> ''
    )
);

CREATE INDEX ix_report_versions_report_status
    ON report_versions (report_id, status, version_number DESC);

CREATE TABLE peer_reviews
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_version_id  UUID        NOT NULL REFERENCES report_versions (id) ON DELETE RESTRICT,
    reviewer_user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assigned_by_user_id UUID       NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assigned_at        TIMESTAMPTZ NOT NULL,
    decision           VARCHAR(24) NOT NULL,
    comments           VARCHAR(4000),
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_peer_reviews_version UNIQUE (report_version_id),
    CONSTRAINT ck_peer_reviews_decision CHECK (
        decision IN ('PENDING', 'CHANGES_REQUESTED', 'APPROVED')
    ),
    CONSTRAINT ck_peer_reviews_decision_time CHECK (
        (decision = 'PENDING' AND reviewed_at IS NULL)
        OR (decision <> 'PENDING' AND reviewed_at IS NOT NULL)
    )
);

CREATE INDEX ix_peer_reviews_reviewer_decision
    ON peer_reviews (reviewer_user_id, decision, assigned_at DESC);
