-- V24: align identity/report vocabulary with the four-role Enterprise SaaS target.
--
-- Forward-only: V1..V23 are immutable history. This migration fails closed when provider/workforce
-- identities cannot be mapped to a customer organization; it never invents tenant scope or permissions.

-- Provider identities have no deterministic customer-organization successor. Refuse the migration
-- before any mutation while provider linkage, service-workforce zones, or provider-manager roles remain.
DO $$
DECLARE
    unmapped_identities TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO unmapped_identities
      FROM (
          SELECT u.email || ' (id=' || u.id || ', zone=' || u.actor_zone
                 || ', provider_id=' || COALESCE(u.provider_id::text, 'NULL')
                 || ', roles=' || COALESCE(string_agg(r.role, ',' ORDER BY r.role), '') || ')' AS label
            FROM users u
            LEFT JOIN user_roles r ON r.user_id = u.id
           WHERE u.provider_id IS NOT NULL
              OR u.actor_zone = 'SERVICE_WORKFORCE'
              OR r.role = 'PROVIDER_MANAGER'
           GROUP BY u.id, u.email, u.actor_zone, u.provider_id
           ORDER BY u.email
           LIMIT 20
      ) offender;

    IF unmapped_identities IS NOT NULL THEN
        RAISE EXCEPTION
            'V24 precheck failed: provider/workforce identities cannot be mapped to a customer organization without an approved deterministic mapping. Resolve or deactivate these identities before retrying: %',
            unmapped_identities;
    END IF;
END $$;

-- Roles that collapse to one target role must not create duplicate (user_id, role) keys.
DO $$
DECLARE
    collisions TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO collisions
      FROM (
          SELECT u.email || ' (id=' || u.id || ', duplicate target role=' ||
                 CASE r.role
                   WHEN 'PLATFORM_ADMIN' THEN 'ADMIN'
                   WHEN 'PLATFORM_OPERATOR' THEN 'ADMIN'
                   WHEN 'CLIENT' THEN 'ORG_ADMIN'
                 END || ')' AS label
            FROM users u
            JOIN user_roles r ON r.user_id = u.id
           WHERE r.role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CLIENT')
           GROUP BY u.id, u.email,
                 CASE r.role
                   WHEN 'PLATFORM_ADMIN' THEN 'ADMIN'
                   WHEN 'PLATFORM_OPERATOR' THEN 'ADMIN'
                   WHEN 'CLIENT' THEN 'ORG_ADMIN'
                 END
          HAVING count(*) > 1
           ORDER BY u.email
           LIMIT 20
      ) offender;

    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION
            'V24 precheck failed: duplicate target role would violate uq_user_roles. Resolve these assignments explicitly before retrying: %',
            collisions;
    END IF;
END $$;

-- All target customer roles require an organization scope; ADMIN must be platform-scoped.
DO $$
DECLARE
    invalid_scopes TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO invalid_scopes
      FROM (
          SELECT u.email || ' (id=' || u.id || ', role=' || r.role
                 || ', zone=' || u.actor_zone
                 || ', organization_id=' || COALESCE(u.organization_id::text, 'NULL') || ')' AS label
            FROM users u
            JOIN user_roles r ON r.user_id = u.id
           WHERE (r.role = 'CLIENT'
                  AND (u.actor_zone <> 'CUSTOMER_ORGANIZATION' OR u.organization_id IS NULL))
              OR (r.role IN ('INSPECTOR', 'MAINTENANCE_ENGINEER')
                  AND (u.actor_zone <> 'CUSTOMER_ORGANIZATION' OR u.organization_id IS NULL))
              OR (r.role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR')
                  AND (u.actor_zone <> 'PLATFORM' OR u.organization_id IS NOT NULL))
           ORDER BY u.email
           LIMIT 20
      ) offender;

    IF invalid_scopes IS NOT NULL THEN
        RAISE EXCEPTION
            'V24 precheck failed: role and tenant scope do not satisfy the target actor-zone contract. Correct the listed rows before retrying: %',
            invalid_scopes;
    END IF;
END $$;

-- Changing a role invalidates credentials issued under the old authorization model.
UPDATE users
   SET auth_version = auth_version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id IN (SELECT user_id FROM user_roles
               WHERE role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CLIENT'));
UPDATE auth_sessions
   SET revoked_at = CURRENT_TIMESTAMP,
       revoked_reason = 'enterprise-role-migration'
 WHERE revoked_at IS NULL
   AND user_id IN (SELECT user_id FROM user_roles
                    WHERE role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CLIENT'));
UPDATE refresh_tokens
   SET revoked_at = CURRENT_TIMESTAMP,
       revoke_reason = 'enterprise-role-migration'
 WHERE revoked_at IS NULL
   AND session_id IN (SELECT s.id
                        FROM auth_sessions s
                        JOIN user_roles r ON r.user_id = s.user_id
                       WHERE r.role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CLIENT'));

-- Drop the legacy check BEFORE producing role values it does not permit.
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS ck_user_roles_role;
UPDATE user_roles
SET role = CASE role
    WHEN 'PLATFORM_ADMIN' THEN 'ADMIN'
    WHEN 'PLATFORM_OPERATOR' THEN 'ADMIN'
    WHEN 'CLIENT' THEN 'ORG_ADMIN'
    ELSE role
END
WHERE role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CLIENT');
ALTER TABLE user_roles
    ADD CONSTRAINT ck_user_roles_role CHECK (role IN ('ADMIN', 'ORG_ADMIN', 'INSPECTOR', 'MAINTENANCE_ENGINEER'));
COMMENT ON CONSTRAINT ck_user_roles_role ON user_roles IS
    'Target role vocabulary: ADMIN, ORG_ADMIN, INSPECTOR, MAINTENANCE_ENGINEER.';

-- SERVICE_WORKFORCE rows were rejected above; the remaining zone vocabulary already matches target.
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_actor_zone;
ALTER TABLE users
    ADD CONSTRAINT ck_users_actor_zone CHECK (actor_zone IN ('PLATFORM', 'CUSTOMER_ORGANIZATION'));

-- Rename report-decision audit columns to match the org-admin target vocabulary.
ALTER TABLE report_versions RENAME COLUMN client_decision_by_user_id TO org_admin_decision_by_user_id;
ALTER TABLE report_versions RENAME COLUMN client_decision_reason TO org_admin_decision_reason;
ALTER TABLE report_versions RENAME CONSTRAINT fk_report_version_client_decision_user TO fk_report_version_org_admin_decision_user;
ALTER TABLE report_versions RENAME CONSTRAINT ck_report_version_client_decision_reason TO ck_report_version_org_admin_decision_reason;

-- No user.provider_id or activation-token mapping remains in the target runtime.
ALTER TABLE users DROP COLUMN IF EXISTS activation_token_hash;
ALTER TABLE users DROP COLUMN IF EXISTS activation_expires_at;
ALTER TABLE users DROP COLUMN IF EXISTS provider_id;
