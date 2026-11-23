package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRatingCriterionRequest(
    @NotBlank @Size(max = 120) String labelEn,
    @NotBlank @Size(max = 120) String labelVi,
    @Size(max = 300) String descriptionEn,
    @Size(max = 300) String descriptionVi,
    Integer displayOrder,
    Boolean active) {}
