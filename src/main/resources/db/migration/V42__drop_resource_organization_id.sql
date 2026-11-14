-- =============================================================================
-- V42: Contract tenancy — drop organization_id from customer-owned resource
-- tables now that owner_customer_id is authoritative and fully backfilled (V41).
--
-- Preconditions (fail-closed):
--   * owner_customer_id is NOT NULL on every row of assets/drones/inspections/
--     service_requests (V41 backfilled from the org owner; the guard below aborts
--     otherwise rather than silently dropping the only remaining tenant key).
--   * every owner_customer_id resolves to a users row (FK integrity).
--
-- This migration is forward-only and irreversible by design. Applied migrations
-- are immutable; do not edit an applied migration.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Fail closed: no unmapped rows, no dangling owners.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM assets WHERE owner_customer_id IS NULL)
     OR EXISTS (SELECT 1 FROM inspections WHERE owner_customer_id IS NULL)
     OR EXISTS (SELECT 1 FROM service_requests WHERE owner_customer_id IS NULL)
     OR EXISTS (SELECT 1 FROM drones WHERE owner_customer_id IS NULL AND inspector_owner_id IS NULL) THEN
    RAISE EXCEPTION 'V42 abort: owner_customer_id backfill incomplete (NULL owners remain)';
  END IF;
  IF EXISTS (SELECT 1 FROM assets a WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = a.owner_customer_id))
     OR EXISTS (SELECT 1 FROM drones d WHERE d.owner_customer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = d.owner_customer_id))
     OR EXISTS (SELECT 1 FROM inspections i WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = i.owner_customer_id))
     OR EXISTS (SELECT 1 FROM service_requests r WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = r.owner_customer_id)) THEN
    RAISE EXCEPTION 'V42 abort: owner_customer_id references missing users';
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 1. Promote owner_customer_id to NOT NULL on customer-owned tables.
--    drones is the exception: inspector-owned partner drones have a NULL
--    owner_customer_id and a non-null inspector_owner_id (see ck_drones_owner_scope).
-- -----------------------------------------------------------------------------
ALTER TABLE assets ALTER COLUMN owner_customer_id SET NOT NULL;
ALTER TABLE inspections ALTER COLUMN owner_customer_id SET NOT NULL;
ALTER TABLE service_requests ALTER COLUMN owner_customer_id SET NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. Drop tenant/composite FKs and direct org FKs FIRST — the composite tenant
--    FKs depend on the org unique indexes dropped in step 3.
-- -----------------------------------------------------------------------------
ALTER TABLE inspections DROP CONSTRAINT IF EXISTS fk_inspections_asset_tenant;
ALTER TABLE inspections DROP CONSTRAINT IF EXISTS inspections_organization_id_fkey;
ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS fk_service_requests_asset_tenant;
ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS fk_service_requests_creator_tenant;
ALTER TABLE assets DROP CONSTRAINT IF EXISTS assets_organization_id_fkey;
ALTER TABLE drones DROP CONSTRAINT IF EXISTS drones_organization_id_fkey;
-- uq_drones_organization_serial is a UNIQUE constraint (not a bare index).
ALTER TABLE drones DROP CONSTRAINT IF EXISTS uq_drones_organization_serial;
-- ck_drones_owner_scope references the dropped organization_id; recreated below on
-- (owner_customer_id, inspector_owner_id).
ALTER TABLE drones DROP CONSTRAINT IF EXISTS ck_drones_owner_scope;

-- -----------------------------------------------------------------------------
-- 3. Drop org-scoped indexes (replaced by owner-scoped indexes in step 5).
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS ix_assets_organization_status;
DROP INDEX IF EXISTS uq_assets_id_organization;
DROP INDEX IF EXISTS uq_assets_organization_asset_code;
DROP INDEX IF EXISTS ix_drones_organization_status;
DROP INDEX IF EXISTS uq_drones_id_organization;
DROP INDEX IF EXISTS ix_inspections_org_asset_status_planned;
DROP INDEX IF EXISTS uq_inspections_id_organization;
DROP INDEX IF EXISTS ix_service_requests_org_created;

-- -----------------------------------------------------------------------------
-- 4. Drop organization_id columns.
-- -----------------------------------------------------------------------------
ALTER TABLE assets DROP COLUMN IF EXISTS organization_id;
ALTER TABLE drones DROP COLUMN IF EXISTS organization_id;
ALTER TABLE inspections DROP COLUMN IF EXISTS organization_id;
ALTER TABLE service_requests DROP COLUMN IF EXISTS organization_id;

-- -----------------------------------------------------------------------------
-- 5. Recreate uniqueness + indexes keyed on owner_customer_id.
-- -----------------------------------------------------------------------------
-- Asset code unique per owner.
CREATE UNIQUE INDEX uq_assets_owner_customer_asset_code
    ON assets (owner_customer_id, asset_code) WHERE asset_code IS NOT NULL;
-- Drone serial unique per owner (customer-owned drones only; inspector-owned drones
-- use uq_drones_inspector_serial on inspector_owner_id).
CREATE UNIQUE INDEX uq_drones_owner_customer_serial
    ON drones (owner_customer_id, serial_number) WHERE owner_customer_id IS NOT NULL;
-- Drones are owned by exactly one of: a customer owner or an inspector owner.
ALTER TABLE drones ADD CONSTRAINT ck_drones_owner_scope CHECK (
    (owner_customer_id IS NOT NULL AND inspector_owner_id IS NULL)
    OR (owner_customer_id IS NULL AND inspector_owner_id IS NOT NULL)
);
-- Idempotent backfill indexes for the owner FK.
CREATE INDEX IF NOT EXISTS ix_assets_owner_customer ON assets (owner_customer_id);
CREATE INDEX IF NOT EXISTS ix_drones_owner_customer ON drones (owner_customer_id);
CREATE INDEX IF NOT EXISTS ix_inspections_owner_customer ON inspections (owner_customer_id);
CREATE INDEX IF NOT EXISTS ix_service_requests_owner_customer ON service_requests (owner_customer_id);
-- Status/name query paths previously served by org indexes.
CREATE INDEX ix_assets_owner_status ON assets (owner_customer_id, status, name);
CREATE INDEX ix_drones_owner_status ON drones (owner_customer_id, serviceability);
CREATE INDEX ix_inspections_owner_asset_status_planned
    ON inspections (owner_customer_id, asset_id, status, planned_start_at);
CREATE INDEX ix_service_requests_owner_created
    ON service_requests (owner_customer_id, created_at DESC);