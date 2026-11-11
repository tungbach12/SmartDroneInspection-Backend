-- V33: Drop legacy maintenance and work-order schema and foreign keys

-- 1. Remove maintenance & reinspection foreign keys and columns from inspections and findings
ALTER TABLE IF EXISTS inspections
    DROP CONSTRAINT IF EXISTS fk_inspections_work_order,
    DROP CONSTRAINT IF EXISTS ck_inspections_type,
    DROP COLUMN IF EXISTS work_order_id CASCADE,
    DROP COLUMN IF EXISTS source_report_version_id CASCADE,
    DROP COLUMN IF EXISTS source_inspection_id CASCADE;

ALTER TABLE IF EXISTS findings
    DROP COLUMN IF EXISTS forwarded_to_maintenance_at CASCADE;

-- 2. Drop dependent maintenance tables with CASCADE
DROP TABLE IF EXISTS reinspection_defect_verifications CASCADE;
DROP TABLE IF EXISTS maintenance_acceptance_decisions CASCADE;
DROP TABLE IF EXISTS maintenance_cost_lines CASCADE;
DROP TABLE IF EXISTS maintenance_change_orders CASCADE;
DROP TABLE IF EXISTS maintenance_work_logs CASCADE;
DROP TABLE IF EXISTS maintenance_tasks CASCADE;
DROP TABLE IF EXISTS maintenance_estimate_versions CASCADE;
DROP TABLE IF EXISTS maintenance_team_members CASCADE;
DROP TABLE IF EXISTS maintenance_reports CASCADE;
DROP TABLE IF EXISTS maintenance_work_orders CASCADE;
DROP TABLE IF EXISTS repair_knowledge_chunks CASCADE;

-- 3. Clean up MAINTENANCE_ENGINEER role references and update ck_users_roles
UPDATE users
SET roles = array_to_string(
    array_remove(string_to_array(roles, ','), 'MAINTENANCE_ENGINEER'),
    ',')
WHERE roles LIKE '%MAINTENANCE_ENGINEER%';

ALTER TABLE IF EXISTS users DROP CONSTRAINT IF EXISTS ck_users_roles;
ALTER TABLE IF EXISTS users
    ADD CONSTRAINT ck_users_roles CHECK (
        ((roles)::text = ''::text)
        OR ((roles)::text ~ '^(ADMIN|MANAGER|CUSTOMER|INSPECTOR)(,(ADMIN|MANAGER|CUSTOMER|INSPECTOR))*$'::text)
    );
