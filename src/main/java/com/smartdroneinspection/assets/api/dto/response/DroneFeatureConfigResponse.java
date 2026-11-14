package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.DroneFeatureConfig;
import java.time.Instant;
import java.util.UUID;

public record DroneFeatureConfigResponse(
    UUID id,
    String code,
    String nameVi,
    String nameEn,
    String descriptionVi,
    String descriptionEn,
    String category,
    boolean isActive,
    int displayOrder,
    Instant createdAt,
    Instant updatedAt) {

  public static DroneFeatureConfigResponse from(DroneFeatureConfig config) {
    return new DroneFeatureConfigResponse(
        config.getId(),
        config.getCode(),
        config.getNameVi(),
        config.getNameEn(),
        config.getDescriptionVi(),
        config.getDescriptionEn(),
        config.getCategory(),
        config.isActive(),
        config.getDisplayOrder(),
        config.getCreatedAt(),
        config.getUpdatedAt());
  }
}
