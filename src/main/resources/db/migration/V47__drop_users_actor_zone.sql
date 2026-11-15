-- =============================================================================
-- V47: Drop redundant users.actor_zone column and ck_users_actor_zone constraint.
-- User access scoping is governed strictly by roles (ADMIN, MANAGER, CUSTOMER, INSPECTOR)
-- and resource ownership (owner_customer_id / inspector_owner_id).
-- =============================================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_actor_zone;
ALTER TABLE users DROP COLUMN IF EXISTS actor_zone;
