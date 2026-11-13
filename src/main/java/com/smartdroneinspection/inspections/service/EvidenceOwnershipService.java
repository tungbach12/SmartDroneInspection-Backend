package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.shared.EvidenceOwnership;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers an evidence tenant-ownership question for another module. Only the boolean crosses the
 * module boundary: the evidence record itself stays inside inspections.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceOwnershipService implements EvidenceOwnership {

  private final EvidenceRepository evidence;

  public EvidenceOwnershipService(EvidenceRepository evidence) {
    this.evidence = evidence;
  }

  @Override
  public boolean existsOwnedByCustomer(UUID evidenceId, UUID ownerCustomerId) {
    if (evidenceId == null || ownerCustomerId == null) {
      return false;
    }
    return evidence.findOwnedByCustomer(evidenceId, ownerCustomerId).isPresent();
  }
}
