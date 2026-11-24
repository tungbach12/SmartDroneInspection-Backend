package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateChecklistTemplateRequest(
    @NotBlank @Size(max = 32) String templateType,
    UUID categoryId,
    @Size(max = 64) String category,
    @NotBlank @Size(max = 300) String label,
    @Size(max = 300) String labelVi,
    @Size(max = 1000) String details,
    @Size(max = 1000) String detailsVi,
    @Size(max = 120) String referenceThreshold,
    Boolean safetyCritical,
    Integer sortOrder,
    Boolean required) {

  public CreateChecklistTemplateRequest(
      String templateType, UUID categoryId, String label, Integer sortOrder, Boolean required) {
    this(templateType, categoryId, null, label, null, null, null, null, null, sortOrder, required);
  }
}
