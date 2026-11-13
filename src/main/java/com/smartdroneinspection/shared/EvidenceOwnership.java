package com.smartdroneinspection.shared;

import java.util.UUID;

/** Public cross-module port for checking whether evidence belongs to a customer owner. */
public interface EvidenceOwnership {

  boolean existsOwnedByCustomer(UUID evidenceId, UUID ownerCustomerId);
}
