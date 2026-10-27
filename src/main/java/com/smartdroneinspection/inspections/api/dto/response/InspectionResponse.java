package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(
    UUID id,
    UUID organizationId,
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
    Instant flightPermitUploadedAt,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String status,
    Instant createdAt,
    Instant updatedAt) {

  public InspectionResponse {
    inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
  }

  public InspectionResponse(
      UUID id,
      UUID organizationId,
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
      Instant flightPermitUploadedAt,
      Instant plannedStartAt,
      Instant plannedEndAt,
      String status,
      Instant createdAt,
      Instant updatedAt) {
    this(
        id,
        organizationId,
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
        flightPermitUploadedAt,
        plannedStartAt,
        plannedEndAt,
        status,
        createdAt,
        updatedAt);
  }
}
