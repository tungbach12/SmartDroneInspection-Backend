package com.smartdroneinspection.inspections.service;

import java.util.List;
import java.util.UUID;

public record ReadinessReviewCommand(
    UUID reviewerCredentialId,
    List<UUID> inspectorCredentialIds,
    List<UUID> droneDocumentIds,
    boolean applicabilityComplete,
    String applicabilityBasisReference,
    String noInspectorCredentialReason,
    String noDroneDocumentReason,
    String humanVerificationBasis) {

  public ReadinessReviewCommand {
    inspectorCredentialIds = List.copyOf(inspectorCredentialIds);
    droneDocumentIds = List.copyOf(droneDocumentIds);
  }
}
