package com.smartdroneinspection.inspections.spi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only public boundary for repair candidates published by MF3. */
public interface RepairCandidateAccess {

  /**
   * Finds published, human-confirmed repair-required findings belonging to the organization,
   * excluding candidates already linked to a live maintenance work order.
   */
  List<RepairCandidate> findRepairCandidates(UUID organizationId);

  Optional<RepairCandidate> findRepairCandidate(
      UUID organizationId, UUID reportVersionId, UUID findingId);
}
