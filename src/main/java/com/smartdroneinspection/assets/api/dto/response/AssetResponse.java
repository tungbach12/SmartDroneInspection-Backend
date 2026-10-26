package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetResponse(
    UUID id,
    String code,
    String name,
    String description,
    String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    String status,
    UUID categoryId,
    Instant createdAt,
    List<String> inspectionItems) {

  public AssetResponse {
    inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
  }

  public AssetResponse(
      UUID id,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String status,
      UUID categoryId,
      Instant createdAt) {
    this(
        id,
        code,
        name,
        description,
        locationText,
        latitude,
        longitude,
        status,
        categoryId,
        createdAt,
        List.of());
  }
}
