package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the inspection list. The report summary travels with the row so the inspections and
 * reports screens read the same source and never disagree about a report's state.
 */
public record InspectionListItemResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    UUID inspectorId,
    String status,
    Instant plannedStartAt,
    Instant plannedEndAt,
    Instant createdAt,
    Instant updatedAt,
    UUID reportId,
    String reportStatus,
    Integer reportVersionNo,
    String pendingAction,
    String inspectionType,
    UUID workOrderId) {

  public InspectionListItemResponse(
      UUID id,
      UUID organizationId,
      UUID assetId,
      UUID inspectorId,
      String status,
      Instant plannedStartAt,
      Instant plannedEndAt,
      Instant createdAt,
      Instant updatedAt,
      UUID reportId,
      String reportStatus,
      Integer reportVersionNo,
      String pendingAction) {
    this(
        id,
        organizationId,
        assetId,
        inspectorId,
        status,
        plannedStartAt,
        plannedEndAt,
        createdAt,
        updatedAt,
        reportId,
        reportStatus,
        reportVersionNo,
        pendingAction,
        "INITIAL",
        null);
  }
}
