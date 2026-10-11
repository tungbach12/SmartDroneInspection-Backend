package com.smartdroneinspection.inspections.service;

import java.util.List;
import java.util.UUID;

public record ReadinessReturnCommand(
    UUID reviewerCredentialId,
    List<UUID> inspectorCredentialIdsObserved,
    List<UUID> droneDocumentIdsObserved,
    String reason) {

  public ReadinessReturnCommand {
    inspectorCredentialIdsObserved = List.copyOf(inspectorCredentialIdsObserved);
    droneDocumentIdsObserved = List.copyOf(droneDocumentIdsObserved);
  }
}
