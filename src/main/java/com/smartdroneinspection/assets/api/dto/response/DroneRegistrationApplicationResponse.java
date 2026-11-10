package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.UUID;

public record DroneRegistrationApplicationResponse(
    UUID id,
    UUID inspectorUserId,
    String inspectorFullName,
    String inspectorEmail,
    String inspectorPilotLicenseNumber,
    String inspectorPilotLicenseClass,
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
    String reviewerFullName,
    Instant reviewedAt,
    UUID droneId,
    Instant createdAt) {

  public static DroneRegistrationApplicationResponse from(
      DroneRegistrationApplication application) {
    return from(application, null, null);
  }

  public static DroneRegistrationApplicationResponse from(
      DroneRegistrationApplication application, UserAccess.ActiveUser inspector) {
    return from(application, inspector, null);
  }

  public static DroneRegistrationApplicationResponse from(
      DroneRegistrationApplication application,
      UserAccess.ActiveUser inspector,
      UserAccess.ActiveUser reviewer) {
    return new DroneRegistrationApplicationResponse(
        application.getId(),
        application.getInspectorUserId(),
        inspector != null ? inspector.fullName() : null,
        inspector != null ? inspector.email() : null,
        inspector != null ? inspector.pilotLicenseNumber() : null,
        inspector != null ? inspector.pilotLicenseClass() : null,
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
        reviewer != null ? reviewer.fullName() : null,
        application.getReviewedAt(),
        application.getDroneId(),
        application.getCreatedAt());
  }
}
