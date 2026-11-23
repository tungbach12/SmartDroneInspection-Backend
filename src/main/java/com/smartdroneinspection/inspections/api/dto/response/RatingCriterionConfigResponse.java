package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.RatingCriterionConfig;
import java.time.Instant;
import java.util.UUID;

public record RatingCriterionConfigResponse(
    UUID id,
    String raterRole,
    String code,
    String labelEn,
    String labelVi,
    String descriptionEn,
    String descriptionVi,
    int displayOrder,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static RatingCriterionConfigResponse from(RatingCriterionConfig entity) {
    return new RatingCriterionConfigResponse(
        entity.getId(),
        entity.getRaterRole().name(),
        entity.getCode(),
        entity.getLabelEn(),
        entity.getLabelVi(),
        entity.getDescriptionEn(),
        entity.getDescriptionVi(),
        entity.getDisplayOrder(),
        entity.isActive(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public RatingCriterionDefinitionResponse toDefinition() {
    return new RatingCriterionDefinitionResponse(
        code, raterRole, labelEn, labelVi, descriptionEn, descriptionVi, displayOrder, active);
  }
}
