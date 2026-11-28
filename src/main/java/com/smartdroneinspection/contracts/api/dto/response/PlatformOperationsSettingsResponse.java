package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.PlatformOperationsSettings;
import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import java.time.Instant;
import java.util.UUID;

public record PlatformOperationsSettingsResponse(
    int assignmentAcceptanceTimeoutHours,
    int serviceRequestLeadDays,
    int apiRequestTimeoutSeconds,
    UUID updatedByUserId,
    Instant updatedAt) {

  public static PlatformOperationsSettingsResponse from(PlatformOperationsSettings settings) {
    return new PlatformOperationsSettingsResponse(
        settings.getAssignmentAcceptanceTimeoutHours(),
        settings.getServiceRequestLeadDays(),
        settings.getApiRequestTimeoutSeconds(),
        settings.getUpdatedByUserId(),
        settings.getUpdatedAt());
  }

  public static PlatformOperationsSettingsResponse defaults() {
    return new PlatformOperationsSettingsResponse(
        PlatformOperationsPolicyPort.DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS,
        PlatformOperationsPolicyPort.DEFAULT_SERVICE_REQUEST_LEAD_DAYS,
        PlatformOperationsPolicyPort.DEFAULT_API_REQUEST_TIMEOUT_SECONDS,
        null,
        null);
  }
}
