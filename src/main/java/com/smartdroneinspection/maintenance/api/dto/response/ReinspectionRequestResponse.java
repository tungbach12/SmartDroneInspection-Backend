package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.ReinspectionRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReinspectionRequestResponse(
    UUID id,
    UUID workOrderId,
    UUID maintenanceReportVersionId,
    int maintenanceReportVersionNo,
    UUID organizationId,
    UUID requestedByUserId,
    List<UUID> findingIds,
    String note,
    String status,
    UUID managerUserId,
    UUID inspectorUserId,
    UUID droneId,
    UUID inspectionId,
    UUID inspectionReportId,
    String customerDecision,
    String customerDecisionNote,
    Instant requestedAt,
    Instant assignedAt,
    Instant decidedAt) {

  public static ReinspectionRequestResponse from(
      ReinspectionRequest request, int versionNo, List<UUID> findingIds) {
    return new ReinspectionRequestResponse(
        request.getId(),
        request.getWorkOrderId(),
        request.getMaintenanceReportVersionId(),
        versionNo,
        request.getOrganizationId(),
        request.getRequestedByUserId(),
        findingIds,
        request.getRequestNote(),
        request.getStatus().name(),
        request.getManagerUserId(),
        request.getInspectorUserId(),
        request.getDroneId(),
        request.getInspectionId(),
        request.getInspectionReportId(),
        request.getCustomerDecision(),
        request.getCustomerDecisionNote(),
        request.getRequestedAt(),
        request.getAssignedAt(),
        request.getDecidedAt());
  }
}
