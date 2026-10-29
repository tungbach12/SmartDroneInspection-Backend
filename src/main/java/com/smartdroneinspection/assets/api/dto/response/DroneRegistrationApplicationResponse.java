package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import java.time.Instant;
import java.util.UUID;

public record DroneRegistrationApplicationResponse(
    UUID id,
    UUID inspectorUserId,
    String serialNumber,
    String model,
    String manufacturer,
    String registrationNumber,
    String registrationFileKey,
    Instant registrationExpiresAt,
    String ownershipFileKey,
    String inspectionFileKey,
    String status,
    String reviewNote,
    UUID reviewedByUserId,
    Instant reviewedAt,
    UUID droneId,
    Instant createdAt) {
  public static DroneRegistrationApplicationResponse from(
      DroneRegistrationApplication application) {
    return new DroneRegistrationApplicationResponse(
        application.getId(),
        application.getInspectorUserId(),
        application.getSerialNumber(),
        application.getModel(),
        application.getManufacturer(),
        application.getRegistrationNumber(),
        application.getRegistrationFileKey(),
        application.getRegistrationExpiresAt(),
        application.getOwnershipFileKey(),
        application.getInspectionFileKey(),
        application.getStatus(),
        application.getReviewNote(),
        application.getReviewedByUserId(),
        application.getReviewedAt(),
        application.getDroneId(),
        application.getCreatedAt());
  }
}
