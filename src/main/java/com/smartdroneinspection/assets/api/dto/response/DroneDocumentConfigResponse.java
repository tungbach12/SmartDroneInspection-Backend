package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.DroneDocumentConfig;
import java.time.Instant;
import java.util.UUID;

public record DroneDocumentConfigResponse(
    UUID id,
    String code,
    String titleVi,
    String titleEn,
    String descriptionVi,
    String descriptionEn,
    boolean isRequired,
    boolean isActive,
    int displayOrder,
    Instant createdAt,
    Instant updatedAt) {

  public static DroneDocumentConfigResponse from(DroneDocumentConfig config) {
    return new DroneDocumentConfigResponse(
        config.getId(),
        config.getCode(),
        config.getTitleVi(),
        config.getTitleEn(),
        config.getDescriptionVi(),
        config.getDescriptionEn(),
        config.isRequired(),
        config.isActive(),
        config.getDisplayOrder(),
        config.getCreatedAt(),
        config.getUpdatedAt());
  }
}
