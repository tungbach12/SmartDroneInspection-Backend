-- =============================================================================
-- V43: Contract tenancy — drop the users org key, zone<->org coupling,
-- audit/notifications org columns, and the organizations table itself.
--
-- Forward-only and irreversible by design. Applied migrations are immutable.
-- owner_customer_id is the sole tenant key after this migration.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. users: drop organization_id, the org index, and the zone<->org constraint.
--    actor_zone is kept but normalized to the role-derived value set; the org
--    coupling constraint is removed because a user no longer belongs to an org.
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS ix_users_organization;
DROP INDEX IF EXISTS uq_users_id_organization;
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_zone_organization;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_organization_id_fkey;
ALTER TABLE users DROP COLUMN IF EXISTS organization_id;

-- Normalize actor_zone to the role-derived values. PROSPECT_CUSTOMER is a
-- pre-approval holding state that is now represented by a DISABLED CUSTOMER user.
UPDATE users SET actor_zone = 'CUSTOMER' WHERE actor_zone = 'CUSTOMER_ORGANIZATION';
UPDATE users SET actor_zone = 'INSPECTOR' WHERE actor_zone = 'INSPECTOR_PARTNER';
UPDATE users SET actor_zone = 'CUSTOMER' WHERE actor_zone = 'PROSPECT_CUSTOMER';

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_actor_zone;
ALTER TABLE users ADD CONSTRAINT ck_users_actor_zone
    CHECK (actor_zone IN ('PLATFORM', 'CUSTOMER', 'INSPECTOR'));

-- -----------------------------------------------------------------------------
-- 2. audit_events: drop organization_id and its index.
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS ix_audit_events_org_aggregate_time;
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS audit_events_organization_id_fkey;
ALTER TABLE audit_events DROP COLUMN IF EXISTS organization_id;

-- -----------------------------------------------------------------------------
-- 3. notifications: drop organization_id; rebuild the inbox index on the
--    per-user access path (access is per recipient_user_id, never per org).
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS ix_notifications_inbox;
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_organization_id_fkey;
ALTER TABLE notifications DROP COLUMN IF EXISTS organization_id;
CREATE INDEX ix_notifications_inbox ON notifications (recipient_user_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 4. Drop the organizations table.
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS organizations CASCADE;