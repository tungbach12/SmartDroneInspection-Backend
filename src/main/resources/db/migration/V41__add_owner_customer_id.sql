-- Phase 1 (expand): add owner_customer_id to customer-owned resource tables.
-- Additive and reversible: organization_id is kept; no drops here.
-- Backfill: owner = the earliest-created ACTIVE CUSTOMER user of the record's organization.
-- Drones: only organization-owned drones (organization_id NOT NULL) get an owner;
-- inspector-owned drones already carry inspector_owner_id and are left untouched.

ALTER TABLE assets ADD COLUMN owner_customer_id uuid REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE drones ADD COLUMN owner_customer_id uuid REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE inspections ADD COLUMN owner_customer_id uuid REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE service_requests ADD COLUMN owner_customer_id uuid REFERENCES users (id) ON DELETE RESTRICT;

WITH customer_owner AS (
    SELECT DISTINCT ON (u.organization_id)
           u.organization_id,
           u.id AS user_id
    FROM users u
    WHERE u.organization_id IS NOT NULL
      AND u.status = 'ACTIVE'
      AND (',' || u.roles || ',') LIKE '%,CUSTOMER,%'
    ORDER BY u.organization_id, u.created_at ASC, u.id ASC
)
UPDATE assets a
SET owner_customer_id = c.user_id
FROM customer_owner c
WHERE a.organization_id = c.organization_id
  AND a.owner_customer_id IS NULL;

WITH customer_owner AS (
    SELECT DISTINCT ON (u.organization_id)
           u.organization_id,
           u.id AS user_id
    FROM users u
    WHERE u.organization_id IS NOT NULL
      AND u.status = 'ACTIVE'
      AND (',' || u.roles || ',') LIKE '%,CUSTOMER,%'
    ORDER BY u.organization_id, u.created_at ASC, u.id ASC
)
UPDATE drones d
SET owner_customer_id = c.user_id
FROM customer_owner c
WHERE d.organization_id IS NOT NULL
  AND d.organization_id = c.organization_id
  AND d.owner_customer_id IS NULL;

WITH customer_owner AS (
    SELECT DISTINCT ON (u.organization_id)
           u.organization_id,
           u.id AS user_id
    FROM users u
    WHERE u.organization_id IS NOT NULL
      AND u.status = 'ACTIVE'
      AND (',' || u.roles || ',') LIKE '%,CUSTOMER,%'
    ORDER BY u.organization_id, u.created_at ASC, u.id ASC
)
UPDATE inspections i
SET owner_customer_id = c.user_id
FROM customer_owner c
WHERE i.organization_id = c.organization_id
  AND i.owner_customer_id IS NULL;

WITH customer_owner AS (
    SELECT DISTINCT ON (u.organization_id)
           u.organization_id,
           u.id AS user_id
    FROM users u
    WHERE u.organization_id IS NOT NULL
      AND u.status = 'ACTIVE'
      AND (',' || u.roles || ',') LIKE '%,CUSTOMER,%'
    ORDER BY u.organization_id, u.created_at ASC, u.id ASC
)
UPDATE service_requests r
SET owner_customer_id = c.user_id
FROM customer_owner c
WHERE r.organization_id = c.organization_id
  AND r.owner_customer_id IS NULL;

-- Fail closed: every organization-owned row must resolve to a customer owner.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM assets WHERE organization_id IS NOT NULL AND owner_customer_id IS NULL)
     OR EXISTS (SELECT 1 FROM drones WHERE organization_id IS NOT NULL AND owner_customer_id IS NULL)
     OR EXISTS (SELECT 1 FROM inspections WHERE organization_id IS NOT NULL AND owner_customer_id IS NULL)
     OR EXISTS (SELECT 1 FROM service_requests WHERE organization_id IS NOT NULL AND owner_customer_id IS NULL) THEN
    RAISE EXCEPTION 'owner_customer_id backfill incomplete: unmapped organization_id rows remain';
  END IF;
END $$;

CREATE INDEX ix_assets_owner_customer ON assets (owner_customer_id);
CREATE INDEX ix_drones_owner_customer ON drones (owner_customer_id);
CREATE INDEX ix_inspections_owner_customer ON inspections (owner_customer_id);
CREATE INDEX ix_service_requests_owner_customer ON service_requests (owner_customer_id);