package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceAcceptanceDecision;
import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import java.time.Instant;
import java.util.UUID;

/** MF4-18/19: an append-only acceptance decision. */
public record AcceptanceDecisionResponse(
    UUID id,
    UUID reportVersionId,
    UUID reviewerUserId,
    AcceptanceDecisionKind decision,
    String technicalComments,
    String acceptanceChecklist,
    String testResult,
    Instant decidedAt) {

  public static AcceptanceDecisionResponse from(MaintenanceAcceptanceDecision decision) {
    return new AcceptanceDecisionResponse(
        decision.getId(),
        decision.getReportVersionId(),
        decision.getReviewerUserId(),
        decision.getDecision(),
        decision.getTechnicalComments(),
        decision.getAcceptanceChecklist(),
        decision.getTestResult(),
        decision.getDecidedAt());
  }
}
