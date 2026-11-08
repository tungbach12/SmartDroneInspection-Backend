-- Extract inspection items into a dedicated inspection_items table
-- linked 1:N to asset_categories (category_id NOT NULL), separated from
-- flight safety checklist_templates (PRE_FLIGHT / POST_FLIGHT).

CREATE TABLE inspection_items (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    label VARCHAR(300) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inspection_items_category
        FOREIGN KEY (category_id) REFERENCES asset_categories(id) ON DELETE CASCADE
);

CREATE INDEX ix_inspection_items_category_order
    ON inspection_items (category_id, sort_order, created_at);

-- Move all CATEGORY_INSPECTION_ITEM templates from checklist_templates into inspection_items
INSERT INTO inspection_items (
    id, category_id, label, sort_order, active, created_at, updated_at, row_version
)
SELECT
    id, category_id, label, sort_order, active, created_at, updated_at, row_version
FROM checklist_templates
WHERE template_type = 'CATEGORY_INSPECTION_ITEM'
  AND category_id IS NOT NULL;

DELETE FROM checklist_templates
WHERE template_type = 'CATEGORY_INSPECTION_ITEM';

-- Tighten checklist_templates so it strictly stores PRE_FLIGHT and POST_FLIGHT checklists only
DROP INDEX IF EXISTS ix_checklist_templates_category_order;

ALTER TABLE checklist_templates
    DROP CONSTRAINT IF EXISTS ck_checklist_templates_category_scope,
    DROP CONSTRAINT IF EXISTS ck_checklist_templates_type,
    DROP CONSTRAINT IF EXISTS fk_checklist_templates_category,
    DROP COLUMN IF EXISTS category_id,
    ADD CONSTRAINT ck_checklist_templates_type
        CHECK (template_type IN ('PRE_FLIGHT', 'POST_FLIGHT'));
