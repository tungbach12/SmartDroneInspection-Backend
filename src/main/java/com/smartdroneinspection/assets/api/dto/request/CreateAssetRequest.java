package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateAssetRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotNull UUID categoryId,
    @NotBlank @Size(max = 500) String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    @Size(max = 1000) String ownershipInformation,
    List<String> inspectionItems) {

  public CreateAssetRequest {
    inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
  }

  public CreateAssetRequest(
      String code,
      String name,
      String description,
      UUID categoryId,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation) {
    this(
        code,
        name,
        description,
        categoryId,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        List.of());
  }
}
