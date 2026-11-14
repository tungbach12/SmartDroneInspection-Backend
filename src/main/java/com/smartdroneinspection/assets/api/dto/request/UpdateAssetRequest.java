package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record UpdateAssetRequest(
    @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @Size(max = 500) String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    @Size(max = 1000) String ownershipInformation,
    List<String> inspectionItems,
    @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 6, fraction = 2)
        BigDecimal structureHeightMeters,
    @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 10, fraction = 2)
        BigDecimal footprintAreaSquareMeters,
    boolean clearCoordinates,
    boolean clearStructureHeight,
    boolean clearFootprintArea,
    boolean clearDescription) {

  public UpdateAssetRequest(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation) {
    this(
        name,
        description,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        null,
        null,
        null,
        false,
        false,
        false,
        false);
  }

  public UpdateAssetRequest(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      List<String> inspectionItems) {
    this(
        name,
        description,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        inspectionItems,
        null,
        null,
        false,
        false,
        false,
        false);
  }
}
