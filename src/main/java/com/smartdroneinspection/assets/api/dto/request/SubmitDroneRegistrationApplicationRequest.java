package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SubmitDroneRegistrationApplicationRequest(
    @NotBlank @Size(max = 128) String serialNumber,
    @Size(max = 1000) String model,
    @Size(max = 200) String manufacturer,
    @NotBlank @Size(max = 100) String registrationNumber,
    @NotBlank @Size(max = 1000) String registrationFileKey,
    @NotNull Instant registrationExpiresAt,
    @NotBlank @Size(max = 1000) String ownershipFileKey,
    @Size(max = 1000) String inspectionFileKey,
    BigDecimal maxWindResistanceMps,
    @Size(max = 32) String opticalZoomLevel,
    Boolean hasThermalCamera,
    Boolean hasRtk,
    Map<String, String> documentFiles,
    List<String> imageFileKeys,
    List<String> features) {

  public SubmitDroneRegistrationApplicationRequest(
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey) {
    this(
        serialNumber,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        null,
        null,
        false,
        false,
        null,
        null,
        null);
  }

  public SubmitDroneRegistrationApplicationRequest(
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      BigDecimal maxWindResistanceMps,
      String opticalZoomLevel,
      Boolean hasThermalCamera,
      Boolean hasRtk,
      Map<String, String> documentFiles,
      List<String> imageFileKeys) {
    this(
        serialNumber,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        maxWindResistanceMps,
        opticalZoomLevel,
        hasThermalCamera,
        hasRtk,
        documentFiles,
        imageFileKeys,
        null);
  }
}
