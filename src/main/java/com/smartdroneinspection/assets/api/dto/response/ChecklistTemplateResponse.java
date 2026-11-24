package com.smartdroneinspection.assets.api.dto.response;

import java.util.UUID;

public record ChecklistTemplateResponse(
    UUID id,
    String templateType,
    UUID categoryId,
    String category,
    String label,
    String labelVi,
    String details,
    String detailsVi,
    String referenceThreshold,
    boolean safetyCritical,
    int sortOrder,
    boolean active,
    boolean required) {

  public ChecklistTemplateResponse(
      UUID id,
      String templateType,
      UUID categoryId,
      String label,
      int sortOrder,
      boolean active,
      boolean required) {
    this(
        id,
        templateType,
        categoryId,
        "GENERAL",
        label,
        null,
        null,
        null,
        null,
        false,
        sortOrder,
        active,
        required);
  }
}
