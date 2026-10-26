-- =============================================================================
-- V2: Role Restructure (PLATFORM_MANAGER, ORG_MANAGER) & B2B Drone Leasing
-- =============================================================================

-- 1. Migrate ORG_ADMIN -> ORG_MANAGER in users.roles and update ck_users_roles
ALTER TABLE users DROP CONSTRAINT ck_users_roles;

UPDATE users
SET roles = replace(roles, 'ORG_ADMIN', 'ORG_MANAGER')
WHERE roles LIKE '%ORG_ADMIN%';

ALTER TABLE users
    ADD CONSTRAINT ck_users_roles CHECK (
        ((roles)::text = ''::text)
        OR ((roles)::text ~ '^(ADMIN|PLATFORM_MANAGER|ORG_MANAGER|INSPECTOR|MAINTENANCE_ENGINEER)(,(ADMIN|PLATFORM_MANAGER|ORG_MANAGER|INSPECTOR|MAINTENANCE_ENGINEER))*$'::text)
    );

-- 2. Platform leasable drone pool
CREATE TABLE platform_drones (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    serial_number character varying(128) NOT NULL UNIQUE,
    model character varying(200) NOT NULL,
    manufacturer character varying(200),
    payload_metadata jsonb,
    monthly_rate_usd numeric(12,2) NOT NULL,
    status character varying(24) NOT NULL,
    notes character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_platform_drones_rate CHECK (monthly_rate_usd > 0),
    CONSTRAINT ck_platform_drones_status CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'LEASED'::character varying, 'MAINTENANCE'::character varying, 'RETIRED'::character varying])::text[])))
);

-- 3. B2B drone lease contracts
CREATE TABLE drone_leases (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    platform_drone_id uuid NOT NULL REFERENCES platform_drones(id) ON DELETE RESTRICT,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_drone_id uuid REFERENCES drones(id) ON DELETE SET NULL,
    requested_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    lease_period_months integer NOT NULL,
    monthly_rate_usd numeric(12,2) NOT NULL,
    agreed_amount_usd numeric(12,2) NOT NULL,
    status character varying(24) NOT NULL,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    reclaimed_at timestamp with time zone,
    external_reference character varying(128),
    paypal_order_id character varying(64),
    paypal_capture_id character varying(64),
    confirmed_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    confirmed_at timestamp with time zone,
    notes character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_drone_leases_period CHECK (lease_period_months >= 1 AND lease_period_months <= 60),
    CONSTRAINT ck_drone_leases_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'RECLAIMED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- 4. Extend customer drones table with lease ownership tracking
ALTER TABLE drones
    ADD COLUMN ownership_type character varying(24) DEFAULT 'OWNED' NOT NULL,
    ADD COLUMN lease_id uuid REFERENCES drone_leases(id) ON DELETE SET NULL,
    ADD COLUMN lease_expires_at timestamp with time zone,
    ADD CONSTRAINT ck_drones_ownership_type CHECK (((ownership_type)::text = ANY ((ARRAY['OWNED'::character varying, 'LEASED'::character varying])::text[])));

-- 5. Secondary indexes for leasing query patterns
CREATE INDEX ix_drone_leases_organization_created ON drone_leases (organization_id, created_at DESC);
CREATE INDEX ix_drone_leases_platform_drone_status ON drone_leases (platform_drone_id, status);
CREATE INDEX ix_drone_leases_status_created ON drone_leases (status, created_at DESC);
CREATE INDEX ix_drones_lease_id ON drones (lease_id) WHERE lease_id IS NOT NULL;
