CREATE TABLE asset_categories
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_asset_categories_code UNIQUE (code),
    CONSTRAINT ck_asset_categories_code_normalized CHECK (code = UPPER(BTRIM(code)))
);

CREATE TABLE checklist_templates
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key       VARCHAR(64) NOT NULL,
    version_number     INTEGER NOT NULL,
    asset_category_id  UUID REFERENCES asset_categories(id) ON DELETE RESTRICT,
    name               VARCHAR(200) NOT NULL,
    description        VARCHAR(2000),
    status             VARCHAR(24) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_checklist_template_version UNIQUE (template_key, version_number),
    CONSTRAINT ck_checklist_template_key_normalized CHECK (template_key = UPPER(BTRIM(template_key))),
    CONSTRAINT ck_checklist_template_version CHECK (version_number > 0),
    CONSTRAINT ck_checklist_template_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_checklist_template_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status IN ('ACTIVE', 'RETIRED') AND published_at IS NOT NULL)
    )
);

CREATE INDEX ix_checklist_templates_category ON checklist_templates(asset_category_id);
CREATE INDEX ix_checklist_templates_status ON checklist_templates(status);

CREATE TABLE checklist_items
(
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id       UUID NOT NULL REFERENCES checklist_templates(id) ON DELETE CASCADE,
    item_code         VARCHAR(64) NOT NULL,
    section_name      VARCHAR(160),
    prompt            VARCHAR(1000) NOT NULL,
    response_type     VARCHAR(24) NOT NULL,
    required          BOOLEAN NOT NULL DEFAULT TRUE,
    display_order     INTEGER NOT NULL,
    guidance          VARCHAR(2000),
    validation_config JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_checklist_item_code UNIQUE (template_id, item_code),
    CONSTRAINT uq_checklist_item_order UNIQUE (template_id, display_order),
    CONSTRAINT ck_checklist_item_code_normalized CHECK (item_code = UPPER(BTRIM(item_code))),
    CONSTRAINT ck_checklist_item_response_type CHECK (
        response_type IN ('PASS_FAIL', 'TEXT', 'NUMBER', 'BOOLEAN', 'CHOICE')
    ),
    CONSTRAINT ck_checklist_item_display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_checklist_items_template ON checklist_items(template_id);

CREATE TABLE assets
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    category_id           UUID NOT NULL REFERENCES asset_categories(id) ON DELETE RESTRICT,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    description           VARCHAR(2000),
    location_text         VARCHAR(500) NOT NULL,
    latitude              NUMERIC(9,6),
    longitude             NUMERIC(9,6),
    ownership_information VARCHAR(1000),
    status                VARCHAR(24) NOT NULL,
    created_by_user_id    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_assets_organization_code UNIQUE (organization_id, code),
    CONSTRAINT ck_assets_code_normalized CHECK (code = UPPER(BTRIM(code))),
    CONSTRAINT ck_assets_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RETIRED')),
    CONSTRAINT ck_assets_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_assets_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX ix_assets_organization_status ON assets(organization_id, status, name);
CREATE INDEX ix_assets_category ON assets(category_id);

CREATE TABLE asset_documents
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id            UUID NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    document_type       VARCHAR(32) NOT NULL,
    file_name           VARCHAR(500) NOT NULL,
    content_type        VARCHAR(160) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    checksum_sha256     CHAR(64) NOT NULL,
    object_key          VARCHAR(1000) NOT NULL,
    document_date       DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_asset_documents_object_key UNIQUE (object_key),
    CONSTRAINT uq_asset_documents_checksum UNIQUE (asset_id, checksum_sha256),
    CONSTRAINT ck_asset_documents_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_asset_documents_asset_time ON asset_documents(asset_id, created_at DESC);

CREATE TABLE inspection_schedules
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id                 UUID NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
    checklist_template_id    UUID NOT NULL REFERENCES checklist_templates(id) ON DELETE RESTRICT,
    frequency_unit           VARCHAR(16) NOT NULL,
    frequency_interval       INTEGER NOT NULL,
    next_due_at              TIMESTAMPTZ NOT NULL,
    last_generated_due_cycle DATE,
    status                   VARCHAR(24) NOT NULL,
    created_by_user_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_inspection_schedules_frequency_unit CHECK (
        frequency_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')
    ),
    CONSTRAINT ck_inspection_schedules_frequency_interval CHECK (frequency_interval > 0),
    CONSTRAINT ck_inspection_schedules_status CHECK (status IN ('ACTIVE', 'PAUSED', 'DISABLED'))
);

CREATE INDEX ix_inspection_schedules_due ON inspection_schedules(status, next_due_at);
CREATE INDEX ix_inspection_schedules_asset ON inspection_schedules(asset_id);
