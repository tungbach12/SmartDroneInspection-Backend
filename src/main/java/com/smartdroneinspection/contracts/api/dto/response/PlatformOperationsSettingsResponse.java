package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.PlatformOperationsSettings;
import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import java.time.Instant;
import java.util.UUID;

public record PlatformOperationsSettingsResponse(
    int assignmentAcceptanceTimeoutHours, UUID updatedByUserId, Instant updatedAt) {

  public static PlatformOperationsSettingsResponse from(PlatformOperationsSettings settings) {
    return new PlatformOperationsSettingsResponse(
        settings.getAssignmentAcceptanceTimeoutHours(),
        settings.getUpdatedByUserId(),
        settings.getUpdatedAt());
  }

  public static PlatformOperationsSettingsResponse defaults() {
    return new PlatformOperationsSettingsResponse(
        PlatformOperationsPolicyPort.DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS, null, null);
  }
}
