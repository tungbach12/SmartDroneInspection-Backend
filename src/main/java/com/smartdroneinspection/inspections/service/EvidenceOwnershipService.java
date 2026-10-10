package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.EvidenceOwnership;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
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
  public boolean existsInOrganization(UUID evidenceId, UUID organizationId) {
    if (evidenceId == null || organizationId == null) {
      return false;
    }
    return evidence.findInOrganization(evidenceId, organizationId).isPresent();
  }
}
