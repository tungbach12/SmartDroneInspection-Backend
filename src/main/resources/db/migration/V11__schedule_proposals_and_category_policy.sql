CREATE TABLE category_frequency_suggestions
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_category_id  UUID NOT NULL REFERENCES asset_categories (id) ON DELETE RESTRICT,
    frequency_unit     VARCHAR(16) NOT NULL,
    frequency_interval INTEGER NOT NULL,
    sort_order         INTEGER NOT NULL DEFAULT 0,
    row_version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_category_frequency_unit
        CHECK (frequency_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    CONSTRAINT ck_category_frequency_interval CHECK (frequency_interval > 0),
    CONSTRAINT uq_category_frequency
        UNIQUE (asset_category_id, frequency_unit, frequency_interval)
);

CREATE INDEX ix_category_frequency_category
    ON category_frequency_suggestions (asset_category_id, sort_order);

CREATE TABLE schedule_proposals
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id               UUID NOT NULL REFERENCES assets (id) ON DELETE RESTRICT,
    checklist_template_id  UUID NOT NULL REFERENCES checklist_templates (id) ON DELETE RESTRICT,
    frequency_unit         VARCHAR(16) NOT NULL,
    frequency_interval     INTEGER NOT NULL,
    status                 VARCHAR(24) NOT NULL,
    manager_note           VARCHAR(500),
    reviewed_by_user_id    UUID,
    selected_by_user_id    UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_schedule_proposal_unit
        CHECK (frequency_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    CONSTRAINT ck_schedule_proposal_interval CHECK (frequency_interval > 0),
    CONSTRAINT ck_schedule_proposal_status
        CHECK (status IN ('GENERATED', 'MANAGER_APPROVED', 'MANAGER_REJECTED',
                          'CLIENT_SELECTED', 'SUPERSEDED')),
    CONSTRAINT uq_schedule_proposal_active
        UNIQUE (asset_id, frequency_unit, frequency_interval)
);

CREATE INDEX ix_schedule_proposals_asset_status
    ON schedule_proposals (asset_id, status);

-- Widen the assets status constraint for the review flow: AssetStatus gains
-- PENDING_REVIEW and REJECTED (see V5 ck_assets_status).
ALTER TABLE assets
    DROP CONSTRAINT ck_assets_status,
    ADD CONSTRAINT ck_assets_status
        CHECK (status IN ('PENDING_REVIEW', 'ACTIVE', 'INACTIVE', 'REJECTED', 'RETIRED'));
