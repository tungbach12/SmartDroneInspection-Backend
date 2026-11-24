package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChecklistTemplateRequest(
    @Size(max = 64) String category,
    @NotBlank @Size(max = 300) String label,
    @Size(max = 300) String labelVi,
    @Size(max = 1000) String details,
    @Size(max = 1000) String detailsVi,
    @Size(max = 120) String referenceThreshold,
    Boolean safetyCritical,
    Integer sortOrder,
    Boolean active,
    Boolean required) {

  public UpdateChecklistTemplateRequest(
      String label, Integer sortOrder, Boolean active, Boolean required) {
    this(null, label, null, null, null, null, null, sortOrder, active, required);
  }
}
