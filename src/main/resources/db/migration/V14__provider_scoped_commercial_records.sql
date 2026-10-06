-- V14: which Provider Organization owns each MF1/MF5 commercial record.
--
-- Forward-only: V1..V13 are applied and are not edited here. This adds four nullable columns, four
-- foreign keys and four indexes. It drops nothing, rewrites no row, and re-declares no constraint.
--
-- Why this exists. A quotation and an order are offers made BY A COMPANY, not by the person who
-- prepared or signed them. Without provider_id on the record itself, every capability gate (SF-03:
-- INSPECTION = VERIFIED before quoting, before assignment, before mission approval) would have to
-- derive the counterparty from users.provider_id of whoever touched the row last - which is the user,
-- not the company, and which is unanswerable for a row written before this column existed.
--
-- Nullability is deliberate. A CHECK cannot forbid NULL here: a CHECK is validated against every row at
-- the moment it is created and this table already holds rows, so forbidding NULL would refuse precisely
-- the deployments that need to migrate. The columns are nullable and the rule is owned by the code that
-- writes them: the MF1/MF5 services refuse a null provider at the transition boundary rather than
-- treating an unnamed provider as eligible.
--
-- Delete behaviour: ON DELETE RESTRICT, matching every other reference in V6/V8. Commercial history
-- names its provider, so deleting a company that has quoted or been invoiced is not something the
-- schema should make easy.
--
-- Pre-check: each column is created NULL in this same file, so no pre-existing row can fail its foreign
-- key - stated, not assumed. The DO block below nevertheless carries the exact negation of each FK,
-- written NOT (<fk predicate>), so the file is also safe to apply over a database where an operator has
-- already populated one of these columns by hand (the remediation this file itself offers).
--
-- Pre-deploy queries (safe to run before deploying; each must return zero rows):
--   SELECT id, provider_id FROM inspection_quotations
--    WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id));
--   SELECT id, provider_id FROM inspection_service_orders
--    WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id));
--   SELECT id, provider_id FROM maintenance_quotations
--    WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id));
--   SELECT id, provider_id FROM maintenance_orders
--    WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id));


ALTER TABLE inspection_quotations    ADD COLUMN IF NOT EXISTS provider_id UUID;
ALTER TABLE inspection_service_orders ADD COLUMN IF NOT EXISTS provider_id UUID;
ALTER TABLE maintenance_quotations   ADD COLUMN IF NOT EXISTS provider_id UUID;
ALTER TABLE maintenance_orders       ADD COLUMN IF NOT EXISTS provider_id UUID;

COMMENT ON COLUMN inspection_quotations.provider_id IS
    'The Provider Organization that offered this quotation. NULL only for a row written before V14; the MF1 service refuses to write one, and a reader denies such a row rather than treating an unnamed provider as eligible.';
COMMENT ON COLUMN inspection_service_orders.provider_id IS
    'The Provider Organization this order contracts with. Copied from the approved quotation in the same transaction, so the two can never disagree. Read directly by the capability gate on order confirmation, assignment and mission approval.';
COMMENT ON COLUMN maintenance_quotations.provider_id IS
    'The maintenance-capable Provider Organization that quoted this work (MF5-02).';
COMMENT ON COLUMN maintenance_orders.provider_id IS
    'The Provider Organization this repair contract is with (MF5-03), copied from the approved quotation.';


-- Pre-check: one block, four branches; each branch is the exact negation of its own foreign key.
--
--    SELECT 'inspection_quotations' AS tbl, id::text FROM inspection_quotations
--     WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
--    UNION ALL
--    SELECT 'inspection_service_orders', id::text FROM inspection_service_orders
--     WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
--    UNION ALL
--    SELECT 'maintenance_quotations', id::text FROM maintenance_quotations
--     WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
--    UNION ALL
--    SELECT 'maintenance_orders', id::text FROM maintenance_orders
--     WHERE NOT (provider_id IS NULL OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id));
--
--    Remediation: clear the unattributable scope (UPDATE <table> SET provider_id = NULL WHERE id = <id>)
--    or create the Provider Organization carrying that id on a database where V12 has committed, then
--    rerun.
DO $$
DECLARE
    dangling TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO dangling
      FROM (
              SELECT 'inspection_quotations ' || id AS label
                FROM inspection_quotations
               WHERE NOT (provider_id IS NULL
                           OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
              UNION ALL
              SELECT 'inspection_service_orders ' || id
                FROM inspection_service_orders
               WHERE NOT (provider_id IS NULL
                           OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
              UNION ALL
              SELECT 'maintenance_quotations ' || id
                FROM maintenance_quotations
               WHERE NOT (provider_id IS NULL
                           OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
              UNION ALL
              SELECT 'maintenance_orders ' || id
                FROM maintenance_orders
               WHERE NOT (provider_id IS NULL
                           OR EXISTS (SELECT 1 FROM provider_organizations p WHERE p.id = provider_id))
               ORDER BY 1
               LIMIT 20
           ) offender;

    IF dangling IS NOT NULL THEN
        RAISE EXCEPTION
            'V14 precheck failed: a commercial record names a Provider Organization that does not exist, and its foreign key would reject it. Rows (first 20): %. Remediation: UPDATE the row SET provider_id = NULL where the scope is unattributable, or create the Provider Organization with that id (V12 table) first, then rerun.',
            dangling;
    END IF;
END $$;

ALTER TABLE inspection_quotations DROP CONSTRAINT IF EXISTS fk_inspection_quotations_provider;
ALTER TABLE inspection_quotations
    ADD CONSTRAINT fk_inspection_quotations_provider FOREIGN KEY (provider_id)
        REFERENCES provider_organizations (id) ON DELETE RESTRICT;

ALTER TABLE inspection_service_orders DROP CONSTRAINT IF EXISTS fk_inspection_service_orders_provider;
ALTER TABLE inspection_service_orders
    ADD CONSTRAINT fk_inspection_service_orders_provider FOREIGN KEY (provider_id)
        REFERENCES provider_organizations (id) ON DELETE RESTRICT;

ALTER TABLE maintenance_quotations DROP CONSTRAINT IF EXISTS fk_maintenance_quotations_provider;
ALTER TABLE maintenance_quotations
    ADD CONSTRAINT fk_maintenance_quotations_provider FOREIGN KEY (provider_id)
        REFERENCES provider_organizations (id) ON DELETE RESTRICT;

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS fk_maintenance_orders_provider;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT fk_maintenance_orders_provider FOREIGN KEY (provider_id)
        REFERENCES provider_organizations (id) ON DELETE RESTRICT;


-- Read paths: "this provider's bid on this request", "everything this provider is contracted for".
CREATE INDEX IF NOT EXISTS ix_inspection_quotations_provider_request
    ON inspection_quotations (provider_id, inspection_request_id, version_number DESC);
CREATE INDEX IF NOT EXISTS ix_inspection_service_orders_provider
    ON inspection_service_orders (provider_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_maintenance_quotations_provider_ticket
    ON maintenance_quotations (provider_id, maintenance_ticket_id, version_number DESC);
CREATE INDEX IF NOT EXISTS ix_maintenance_orders_provider
    ON maintenance_orders (provider_id, created_at DESC);
