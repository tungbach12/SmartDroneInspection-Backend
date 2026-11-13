package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InspectorDroneResponse(
    UUID id,
    String serialNumber,
    String model,
    String manufacturer,
    String serviceability,
    String registrationNumber,
    Instant registrationExpiresAt,
    UUID inspectorOwnerId,
    String inspectorFullName,
    String inspectorEmail,
    Double mtowKg,
    String legalCategory,
    String requiredLicenseClass,
    String registrationExpirationStatus,
    Long registrationRemainingDays,
    BigDecimal maxWindResistanceMps,
    BigDecimal maxAltitudeMeters,
    String opticalZoomLevel,
    boolean hasThermalCamera,
    boolean hasRtk,
    List<String> features,
    Map<String, String> documentFiles,
    List<String> imageFileKeys,
    String primaryImageKey) {

  public static InspectorDroneResponse from(Drone drone) {
    return from(drone, null);
  }

  public static InspectorDroneResponse from(Drone drone, UserAccess.ActiveUser inspector) {
    Instant expiresAt = drone.getRegistrationExpiresAt();
    Long remainingDays =
        expiresAt != null ? Duration.between(Instant.now(), expiresAt).toDays() : null;
    String expirationStatus =
        remainingDays == null
            ? "UNKNOWN"
            : remainingDays < 0 ? "EXPIRED" : remainingDays <= 30 ? "EXPIRING_SOON" : "VALID";
    return new InspectorDroneResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getServiceability().name(),
        drone.getRegistrationNumber(),
        expiresAt,
        drone.getInspectorOwnerId(),
        inspector != null ? inspector.fullName() : null,
        inspector != null ? inspector.email() : null,
        drone.resolveMtowKg(),
        drone.resolveLegalCategory(),
        drone.requiresClassBLicense() ? "CLASS_B" : "CLASS_A",
        expirationStatus,
        remainingDays,
        drone.getMaxWindResistanceMps(),
        drone.getMaxAltitudeMeters(),
        drone.getOpticalZoomLevel(),
        drone.isHasThermalCamera(),
        drone.isHasRtk(),
        drone.getFeatures(),
        drone.getDocumentFiles(),
        drone.getImageFileKeys(),
        drone.getPrimaryImageKey());
  }
}
