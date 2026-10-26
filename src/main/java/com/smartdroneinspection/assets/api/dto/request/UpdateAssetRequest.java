package com.smartdroneinspection.assets.api.dto.request;

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
    List<String> inspectionItems) {

  public UpdateAssetRequest(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation) {
    this(name, description, locationText, latitude, longitude, ownershipInformation, null);
  }
}
