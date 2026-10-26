-- Preserve the applied V1/V2 checksums while removing the remaining unmapped WF1 columns.
ALTER TABLE asset_categories
    DROP CONSTRAINT asset_categories_organization_id_fkey,
    DROP COLUMN organization_id;

ALTER TABLE assets
    DROP COLUMN asset_type;

ALTER TABLE asset_documents
    DROP COLUMN document_version,
    DROP COLUMN status,
    DROP COLUMN updated_at;
