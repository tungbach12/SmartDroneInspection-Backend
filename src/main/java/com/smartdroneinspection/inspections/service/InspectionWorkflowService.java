package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.InspectionWorkflowStateResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionWorkflowService {

  private final EvidenceService evidenceService;
  private final FieldSessionRepository sessions;

  public InspectionWorkflowService(
      EvidenceService evidenceService, FieldSessionRepository sessions) {
    this.evidenceService = evidenceService;
    this.sessions = sessions;
  }

  @Transactional(readOnly = true)
  public InspectionWorkflowStateResponse state(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    boolean canWithdrawAssignment =
        inspection.isInspectedBy(actorId)
            && inspection.getStatus() == InspectionStatus.SCHEDULED
            && inspection.canWithdrawAssignment(
                sessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
    return new InspectionWorkflowStateResponse(
        inspection.getStatus(),
        inspection.getFindingsConfirmedAt(),
        inspection.getFlightPermitVerificationStatus(),
        canWithdrawAssignment);
  }
}
