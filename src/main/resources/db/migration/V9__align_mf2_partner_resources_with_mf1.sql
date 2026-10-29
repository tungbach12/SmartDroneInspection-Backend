-- Preserve existing organization-owned partner rows while supporting the canonical
-- organization-independent Inspector partners and Inspector-owned drones from MF1.
ALTER TABLE service_requests
    ALTER COLUMN contract_checksum_sha256 TYPE varchar(64);

ALTER TABLE contract_partner_inspectors
    DROP CONSTRAINT fk_contract_partner_inspectors_inspector,
    ALTER COLUMN organization_id DROP NOT NULL,
    ADD CONSTRAINT fk_contract_partner_inspectors_user
        FOREIGN KEY (inspector_user_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE contract_partner_drones
    DROP CONSTRAINT fk_contract_partner_drones_drone,
    ALTER COLUMN organization_id DROP NOT NULL,
    ADD COLUMN inspector_owner_id uuid;

UPDATE contract_partner_drones partner
SET inspector_owner_id = drone.inspector_owner_id
FROM drones drone
WHERE drone.id = partner.drone_id
  AND drone.inspector_owner_id IS NOT NULL;

ALTER TABLE contract_partner_drones
    ADD CONSTRAINT ck_contract_partner_drones_owner_scope CHECK (
        (organization_id IS NOT NULL AND inspector_owner_id IS NULL)
        OR (organization_id IS NULL AND inspector_owner_id IS NOT NULL)
    ),
    ADD CONSTRAINT fk_contract_partner_drones_drone
        FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_contract_partner_drones_org_drone
        FOREIGN KEY (drone_id, organization_id) REFERENCES drones(id, organization_id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_drones_id_inspector_owner ON drones (id, inspector_owner_id);
ALTER TABLE contract_partner_drones
    ADD CONSTRAINT fk_contract_partner_drones_inspector_owner
        FOREIGN KEY (drone_id, inspector_owner_id) REFERENCES drones(id, inspector_owner_id) ON DELETE RESTRICT;
CREATE INDEX ix_contract_partner_drones_owner_status
    ON contract_partner_drones (inspector_owner_id, status);
