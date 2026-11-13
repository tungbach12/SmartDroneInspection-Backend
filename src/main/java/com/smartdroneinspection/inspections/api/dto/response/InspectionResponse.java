package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(
    UUID id,
    UUID ownerCustomerId,
    UUID assetId,
    String assetName,
    String locationText,
    BigDecimal assetLatitude,
    BigDecimal assetLongitude,
    List<String> inspectionItems,
    UUID inspectorId,
    String inspectorName,
    UUID droneId,
    String droneSerialNumber,
    String droneModel,
    String droneServiceability,
    String flightPermitFileKey,
    String flightPermitFileName,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String status,
    Instant createdAt,
    Instant updatedAt,
    String inspectionType,
    UUID sourceInspectionId) {

  public InspectionResponse {
    inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
  }

  public InspectionResponse(
      UUID id,
      UUID ownerCustomerId,
      UUID assetId,
      String assetName,
      String locationText,
      List<String> inspectionItems,
      UUID inspectorId,
      String inspectorName,
      UUID droneId,
      String droneSerialNumber,
      String droneModel,
      String droneServiceability,
      String flightPermitFileKey,
      String flightPermitFileName,
      Instant plannedStartAt,
      Instant plannedEndAt,
      String status,
      Instant createdAt,
      Instant updatedAt) {
    this(
        id,
        ownerCustomerId,
        assetId,
        assetName,
        locationText,
        null,
        null,
        inspectionItems,
        inspectorId,
        inspectorName,
        droneId,
        droneSerialNumber,
        droneModel,
        droneServiceability,
        flightPermitFileKey,
        flightPermitFileName,
        plannedStartAt,
        plannedEndAt,
        status,
        createdAt,
        updatedAt,
        "INITIAL",
        null);
  }

  public static InspectionResponse from(
      com.smartdroneinspection.inspections.domain.Inspection inspection) {
    return new InspectionResponse(
        inspection.getId(),
        inspection.getOwnerCustomerId(),
        inspection.getAssetId(),
        null,
        null,
        null,
        null,
        List.of(),
        inspection.getInspectorId(),
        null,
        inspection.getDroneId(),
        null,
        null,
        null,
        inspection.getFlightPermitFileKey(),
        inspection.getFlightPermitFileName(),
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        inspection.getStatus().name(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt(),
        inspection.getInspectionType() != null ? inspection.getInspectionType().name() : "INITIAL",
        null);
  }
}
