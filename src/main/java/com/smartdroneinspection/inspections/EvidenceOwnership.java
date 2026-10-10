package com.smartdroneinspection.inspections;

import java.util.UUID;

/**
 * Tenant-ownership check for an evidence reference made from another module. It answers only
 * whether the evidence belongs to the given organization; a foreign or unknown identifier are both
 * reported the same way, so a caller cannot probe another tenant for existing evidence.
 */
public interface EvidenceOwnership {

  boolean existsInOrganization(UUID evidenceId, UUID organizationId);
}
