-- Rename role codes without rewriting the already-applied authentication migration.
-- Existing users keep their role assignments while the application moves to the
-- shorter canonical codes: ADMIN, CLIENT, and SERVICE_MANAGER.

ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS ck_user_roles_role;

UPDATE user_roles
SET role = CASE role
               WHEN 'PLATFORM_ADMINISTRATOR' THEN 'ADMIN'
               WHEN 'ORGANIZATION_MANAGER' THEN 'CLIENT'
               WHEN 'SERVICE_OPERATIONS_MANAGER' THEN 'SERVICE_MANAGER'
               ELSE role
           END
WHERE role IN ('PLATFORM_ADMINISTRATOR', 'ORGANIZATION_MANAGER', 'SERVICE_OPERATIONS_MANAGER');

ALTER TABLE user_roles
    ADD CONSTRAINT ck_user_roles_role CHECK (role IN (
        'ADMIN', 'CLIENT', 'SERVICE_MANAGER', 'INSPECTOR', 'MAINTENANCE_ENGINEER'
    ));
