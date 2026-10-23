package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.InspectionWorkflowStateResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionWorkflowService {

  private final EvidenceService evidenceService;

  public InspectionWorkflowService(EvidenceService evidenceService) {
    this.evidenceService = evidenceService;
  }

  @Transactional(readOnly = true)
  public InspectionWorkflowStateResponse state(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    return new InspectionWorkflowStateResponse(
        inspection.getStatus(),
        inspection.getFindingsConfirmedAt(),
        inspection.getFindingsSnapshotHash());
  }
}
