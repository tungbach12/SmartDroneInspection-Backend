-- V13: canonical role vocabulary - exactly six role codes.
--
-- Forward-only: V1..V12 are applied and are not edited here. This file rewrites role codes inside its
-- own transaction and re-declares one CHECK constraint; it drops no table, no column and no row.
--
-- Authority: business-flows.md v3.3 §II fixes the canonical six -
--   PLATFORM_ADMIN, PLATFORM_OPERATOR, CLIENT, PROVIDER_MANAGER, INSPECTOR, MAINTENANCE_ENGINEER
-- and database-design.md §6.1 (user_roles) says the same: "One of the six canonical role codes".
-- After this file, user_roles.role accepts ONLY those six values.
--
-- Legacy mapping actually applied, and why each row is or is not translated:
--
--   ADMIN                 -> PLATFORM_ADMIN       UNAMBIGUOUS. V3/V4 created exactly one platform
--                            technical role and RolePolicy allows a PLATFORM-zone identity to hold
--                            only that role, so there is no second candidate successor.
--   CLIENT                -> CLIENT               UNAMBIGUOUS. Already canonical.
--   INSPECTOR             -> INSPECTOR            UNAMBIGUOUS. Already canonical.
--   MAINTENANCE_ENGINEER  -> MAINTENANCE_ENGINEER UNAMBIGUOUS. Already canonical.
--   SERVICE_MANAGER       -> (no successor)       AMBIGUOUS, REFUSED. See section 1.
--
-- Why SERVICE_MANAGER is refused rather than guessed. It has two canonical successors:
-- PLATFORM_OPERATOR (platform business operations) and PROVIDER_MANAGER (the provider company's
-- representative). This schema cannot distinguish them from data: RolePolicy puts every SERVICE_MANAGER
-- in the SERVICE_WORKFORCE zone, but users.provider_id - the only fact that would say WHICH company the
-- user speaks for - is NULL on every row until V12 is remediated, and a zone-based guess would silently
-- grant platform commercial powers (policy publication, vetting, commission invoicing) or provider
-- commercial powers (quotation submission, receipt confirmation) to the wrong identity. The project
-- migration policy prefers refusal to a silent wrong answer, so the migration is BLOCKED while any such
-- row exists, with the remediation spelled out below.
--
-- actor zones are deliberately NOT renamed here. users.actor_zone stays PLATFORM /
-- CUSTOMER_ORGANIZATION / SERVICE_WORKFORCE, because the deployed ActorZone vocabulary is still those
-- three values; renaming them is a separate, coordinated change and is out of scope for this file.
--
-- Session invalidation (section 4): a role rewrite changes what an identity is authorised to do, so
-- every credential minted under the old role must not survive it. auth_version is the already
-- established invalidation lever (the JWT carries it and AuthService compares it on every request), so
-- bumping it retires outstanding credentials without touching token rows; the rows are also marked
-- revoked for audit. Only users actually holding ADMIN are touched, keyed BEFORE the rewrite, which
-- makes the statement a no-op on any rerun (a rerun finds no ADMIN row to rewrite and none to revoke).
--
-- Pre-checks: each DO block below runs FIRST and raises naming the offending rows, because PostgreSQL
-- DDL is transactional and a constraint a populated database violates aborts the whole file with no
-- partial state. The pre-deploy remediation query for each block is quoted directly above it.


-- 1. Pre-check: refuse to migrate while any ambiguous legacy SERVICE_MANAGER row exists.
--
--    SELECT u.id, u.email, u.actor_zone
--      FROM users u
--      JOIN user_roles r ON r.user_id = u.id
--     WHERE r.role = 'SERVICE_MANAGER'
--     ORDER BY u.email;
--
--    Remediation (this is the deliberate fail-closed gate; the migration is blocked, not guessed):
--      a) decide, per user above, whether the identity is platform governance or a provider company's
--         representative, then
--           UPDATE user_roles SET role = 'PLATFORM_OPERATOR'
--            WHERE role = 'SERVICE_MANAGER' AND user_id = <platform-side user id>;
--           UPDATE user_roles SET role = 'PROVIDER_MANAGER'
--            WHERE role = 'SERVICE_MANAGER' AND user_id = <provider-side user id>;
--      b) for a PROVIDER_MANAGER, also scope the identity once V12 has a Provider Organization:
--           UPDATE users SET provider_id = <provider organization id> WHERE id = <user id>;
--      c) deactivate or re-provision any identity that no longer represents anything, then rerun.
DO $$
DECLARE
    ambiguous_users TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO ambiguous_users
      FROM (
              SELECT u.email || ' (id=' || u.id || ', zone=' || u.actor_zone || ')' AS label
                FROM users u
                JOIN user_roles r ON r.user_id = u.id
               WHERE r.role = 'SERVICE_MANAGER'
               ORDER BY u.email
               LIMIT 20
           ) offender;

    IF ambiguous_users IS NOT NULL THEN
        RAISE EXCEPTION
            'V13 precheck failed: the legacy SERVICE_MANAGER role has two canonical successors, PLATFORM_OPERATOR and PROVIDER_MANAGER, and nothing in the current data distinguishes them, so this migration is blocked instead of guessing. Migrating on any other signal would silently grant platform or provider commercial powers to the wrong identity. Users still holding SERVICE_MANAGER (first 20): %. Remediation: for each user decide platform-side (-> PLATFORM_OPERATOR) or provider-side (-> PROVIDER_MANAGER, with users.provider_id populated from V12), update user_roles.role accordingly, then rerun this migration.',
            ambiguous_users;
    END IF;
END $$;


-- 2. Pre-check: refuse to migrate while any role code is outside BOTH the legacy vocabulary this file
--    rewrites and the canonical six. Accepting the canonical six as legal is what makes this file
--    rerun-safe: after a successful run user_roles holds PLATFORM_ADMIN, and a legacy-only list would
--    classify that correct value as unmappable, abort, and tell an operator to destroy correct data.
--
--    SELECT role, count(*) FROM user_roles
--     WHERE role NOT IN ('ADMIN', 'CLIENT', 'SERVICE_MANAGER', 'INSPECTOR', 'MAINTENANCE_ENGINEER',
--                        'PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'PROVIDER_MANAGER')
--     GROUP BY role;
--
--    Remediation: no automatic mapping exists. Assign each value to its intended canonical role by
--    hand, or remove the assignment, then rerun this migration.
DO $$
DECLARE
    unknown_roles TEXT;
BEGIN
    SELECT string_agg(role || ' (x' || role_count || ')', '; ')
      INTO unknown_roles
      FROM (
              SELECT role, count(*) AS role_count
                FROM user_roles
               WHERE role NOT IN ('ADMIN', 'CLIENT', 'SERVICE_MANAGER', 'INSPECTOR',
                                  'MAINTENANCE_ENGINEER', 'PLATFORM_ADMIN', 'PLATFORM_OPERATOR',
                                  'PROVIDER_MANAGER')
               GROUP BY role
               ORDER BY role
           ) offender;

    IF unknown_roles IS NOT NULL THEN
        RAISE EXCEPTION
            'V13 precheck failed: user_roles.role holds values outside both the legacy vocabulary this migration rewrites and the canonical six: %. Remediation: there is no automatic mapping for these values. Assign each to its intended canonical role by hand, or remove the assignment, then rerun this migration.',
            unknown_roles;
    END IF;
END $$;


-- 3. Pre-check: a user holding ADMIN together with another role cannot be split automatically, because
--    the platform invariant allows exactly one platform role and removing the wrong assignment would
--    silently change what the identity can do. RolePolicy has refused to create such a row since V3,
--    so this gate only ever fires on data written outside the application.
--
--    SELECT u.id, u.email, string_agg(r.role, ', ' ORDER BY r.role)
--      FROM users u JOIN user_roles r ON r.user_id = u.id
--     GROUP BY u.id, u.email
--    HAVING count(*) FILTER (WHERE r.role = 'ADMIN') > 0 AND count(*) > 1;
--
--    Remediation: keep exactly one role on each listed user (normally ADMIN -> PLATFORM_ADMIN) and
--    remove the extra assignment, then rerun this migration.
DO $$
DECLARE
    multi_role_admins TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO multi_role_admins
      FROM (
              SELECT u.email || ' (id=' || u.id || ', roles='
                     || string_agg(r.role, ',' ORDER BY r.role) || ')' AS label
                FROM users u
                JOIN user_roles r ON r.user_id = u.id
               GROUP BY u.id, u.email
              HAVING count(*) FILTER (WHERE r.role = 'ADMIN') > 0
                 AND count(*) > 1
               ORDER BY u.email
               LIMIT 20
           ) offender;

    IF multi_role_admins IS NOT NULL THEN
        RAISE EXCEPTION
            'V13 precheck failed: a PLATFORM_ADMIN may hold exactly one role, but these users hold ADMIN together with another role (first 20): %. The role that must be kept cannot be determined from the data. Remediation: keep only ADMIN on each listed user and remove the extra assignment, then rerun this migration.',
            multi_role_admins;
    END IF;
END $$;


-- 4. Retire credentials minted under the pre-migration role.
--
--    Keyed on role = 'ADMIN' and evaluated BEFORE the rewrite below: that is the exact set of users
--    this run changes. Keyed on role = 'PLATFORM_ADMIN' instead, the statement would bump every admin
--    again on every rerun. Rows are revoked, never deleted: the audit trail of who held which role when
--    must survive the migration.
UPDATE users
   SET auth_version = auth_version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE actor_zone = 'PLATFORM'
   AND id IN (SELECT user_id FROM user_roles WHERE role = 'ADMIN');

UPDATE auth_sessions
   SET revoked_at = CURRENT_TIMESTAMP,
       revoked_reason = 'canonical-role-migration'
 WHERE revoked_at IS NULL
   AND user_id IN (SELECT user_id FROM user_roles WHERE role = 'ADMIN');

UPDATE refresh_tokens
   SET revoked_at = CURRENT_TIMESTAMP,
       revoke_reason = 'canonical-role-migration'
 WHERE revoked_at IS NULL
   AND session_id IN (SELECT s.id
                        FROM auth_sessions s
                        JOIN user_roles r ON r.user_id = s.user_id
                       WHERE r.role = 'ADMIN');


-- 5. Role codes: apply the single approved rewrite, then enforce the canonical six.
--
--    The constraint is dropped before the rewrite and re-added after it, the ordering V4 already uses,
--    because V4's ck_user_roles_role still enforces the LEGAL list ('ADMIN', 'CLIENT', 'SERVICE_MANAGER',
--    'INSPECTOR', 'MAINTENANCE_ENGINEER') at this point and would reject the first row rewritten to
--    PLATFORM_ADMIN. The window is not unguarded: section 1-3 pre-checks have already proven what the
--    data holds, the rewrite and the re-declaration run in this file's transaction, and any failure
--    rolls the whole file back.
--
--    CLIENT, INSPECTOR and MAINTENANCE_ENGINEER are already canonical and are never written. Section 1
--    has already guaranteed no SERVICE_MANAGER row exists, so nothing is translated for it: were one to
--    appear between the pre-check and this statement the new CHECK would refuse it rather than pick a
--    successor.
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS ck_user_roles_role;

UPDATE user_roles SET role = 'PLATFORM_ADMIN' WHERE role = 'ADMIN';

ALTER TABLE user_roles
    ADD CONSTRAINT ck_user_roles_role CHECK (role IN (
        'PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CLIENT',
        'PROVIDER_MANAGER', 'INSPECTOR', 'MAINTENANCE_ENGINEER'
    ));

COMMENT ON CONSTRAINT ck_user_roles_role ON user_roles IS
    'Exactly the six canonical role codes of business-flows.md v3.3 section II. Legacy ADMIN is migrated to PLATFORM_ADMIN by V13; SERVICE_MANAGER is refused at migration time because its two successors cannot be told apart from data. 400/409 handling for unknown role codes lives in the application, not here.';

-- Role-scoped lookups: every authorization query that asks "which identities hold role X" scans this
-- table today without an index on role.
CREATE INDEX IF NOT EXISTS ix_user_roles_role ON user_roles (role);
