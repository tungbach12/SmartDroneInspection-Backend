package com.smartdroneinspection.shared;

import java.util.UUID;

/** Public cross-module port for checking whether evidence belongs to an organization. */
public interface EvidenceOwnership {

  boolean existsInOrganization(UUID evidenceId, UUID organizationId);
}
