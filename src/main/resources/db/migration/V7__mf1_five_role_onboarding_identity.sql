-- =============================================================================
-- V7: MF1 Five-Role Identity Vocabulary & Onboarding Review Tables
-- =============================================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_roles;

UPDATE users
SET auth_version = auth_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE roles LIKE '%ORG_MANAGER%'
   OR roles LIKE '%ORG_ADMIN%'
   OR roles LIKE '%PLATFORM_MANAGER%'
   OR roles LIKE '%ADMIN%';

UPDATE auth_sessions
SET revoked_at = CURRENT_TIMESTAMP,
    revoked_reason = 'mf1-role-vocabulary-migration'
WHERE revoked_at IS NULL
  AND user_id IN (
    SELECT id FROM users
    WHERE roles LIKE '%ORG_MANAGER%'
       OR roles LIKE '%ORG_ADMIN%'
       OR roles LIKE '%PLATFORM_MANAGER%'
       OR roles LIKE '%ADMIN%'
  );

UPDATE users
SET roles = replace(replace(replace(roles, 'PLATFORM_MANAGER', 'MANAGER'), 'ORG_MANAGER', 'CUSTOMER'), 'ORG_ADMIN', 'CUSTOMER')
WHERE roles LIKE '%PLATFORM_MANAGER%'
   OR roles LIKE '%ORG_MANAGER%'
   OR roles LIKE '%ORG_ADMIN%';

ALTER TABLE users
    ADD CONSTRAINT ck_users_roles CHECK (
        ((roles)::text = ''::text)
        OR ((roles)::text ~ '^(ADMIN|MANAGER|CUSTOMER|INSPECTOR|MAINTENANCE_ENGINEER)(,(ADMIN|MANAGER|CUSTOMER|INSPECTOR|MAINTENANCE_ENGINEER))*$'::text)
    );

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_actor_zone;
ALTER TABLE users
    ADD CONSTRAINT ck_users_actor_zone CHECK (
        actor_zone IN ('PLATFORM', 'CUSTOMER_ORGANIZATION', 'INSPECTOR_PARTNER', 'PROSPECT_CUSTOMER')
    );

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_zone_organization;
ALTER TABLE users
    ADD CONSTRAINT ck_users_zone_organization CHECK (
        (actor_zone = 'CUSTOMER_ORGANIZATION' AND organization_id IS NOT NULL)
        OR (actor_zone IN ('PLATFORM', 'INSPECTOR_PARTNER', 'PROSPECT_CUSTOMER') AND organization_id IS NULL)
    );

ALTER TABLE users
    ALTER COLUMN pilot_license_number TYPE VARCHAR(128),
    ALTER COLUMN pilot_license_class TYPE VARCHAR(64);

CREATE TABLE prospective_customer_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    organization_name VARCHAR(200) NOT NULL,
    organization_code VARCHAR(64) NOT NULL,
    asset_category_id UUID NOT NULL REFERENCES asset_categories(id) ON DELETE RESTRICT,
    asset_code VARCHAR(64) NOT NULL,
    asset_name VARCHAR(200) NOT NULL,
    asset_description VARCHAR(2000),
    asset_location VARCHAR(500) NOT NULL,
    latitude NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    ownership_information VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    airspace_classification VARCHAR(32) NOT NULL,
    review_note VARCHAR(2000),
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_prospective_customer_pending_org_code
    ON prospective_customer_registrations (organization_code) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_prospective_customer_pending_asset_code
    ON prospective_customer_registrations (organization_code, asset_code) WHERE status = 'PENDING';
CREATE INDEX ix_prospective_customer_status_created
    ON prospective_customer_registrations (status, created_at);

CREATE TABLE inspector_partner_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(64),
    pilot_license_number VARCHAR(128) NOT NULL,
    pilot_license_class VARCHAR(64) NOT NULL,
    pilot_license_expires_at DATE NOT NULL,
    pilot_license_file_key VARCHAR(1000) NOT NULL,
    partner_contract_file_key VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note VARCHAR(2000),
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_inspector_partner_pending_email
    ON inspector_partner_applications (normalized_email) WHERE status = 'PENDING';
CREATE INDEX ix_inspector_partner_status_created
    ON inspector_partner_applications (status, created_at);

ALTER TABLE drones ALTER COLUMN organization_id DROP NOT NULL;

DO $$
DECLARE duplicate_serials TEXT;
BEGIN
  SELECT string_agg(serial_number, ', ' ORDER BY serial_number)
    INTO duplicate_serials
    FROM (
      SELECT serial_number FROM drones GROUP BY serial_number HAVING count(*) > 1 LIMIT 20
    ) duplicates;
  IF duplicate_serials IS NOT NULL THEN
    RAISE EXCEPTION 'V7 precheck failed: duplicate Drone serials prevent Inspector ownership: %', duplicate_serials;
  END IF;
END $$;

CREATE UNIQUE INDEX uq_drones_serial_number_global ON drones (serial_number);
ALTER TABLE drones
    ADD COLUMN IF NOT EXISTS inspector_owner_id UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE drones
    ADD CONSTRAINT ck_drones_owner_scope CHECK (
        (organization_id IS NOT NULL AND inspector_owner_id IS NULL)
        OR (organization_id IS NULL AND inspector_owner_id IS NOT NULL)
    );
CREATE UNIQUE INDEX uq_drones_inspector_serial ON drones (inspector_owner_id, serial_number)
    WHERE inspector_owner_id IS NOT NULL;
CREATE INDEX ix_drones_inspector_owner ON drones (inspector_owner_id);

CREATE TABLE drone_registration_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspector_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    serial_number VARCHAR(128) NOT NULL,
    model VARCHAR(200),
    manufacturer VARCHAR(200),
    registration_number VARCHAR(100) NOT NULL,
    registration_file_key VARCHAR(1000) NOT NULL,
    registration_expires_at TIMESTAMPTZ NOT NULL,
    ownership_file_key VARCHAR(1000) NOT NULL,
    inspection_file_key VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note VARCHAR(2000),
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    drone_id UUID REFERENCES drones(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_drone_registration_pending_serial
    ON drone_registration_applications (serial_number) WHERE status = 'PENDING';
CREATE INDEX ix_drone_registration_status_created
    ON drone_registration_applications (status, created_at);
