package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRatingCriterionRequest(
    @NotNull RaterRole raterRole,
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 120) String labelEn,
    @NotBlank @Size(max = 120) String labelVi,
    @Size(max = 300) String descriptionEn,
    @Size(max = 300) String descriptionVi,
    Integer displayOrder,
    Boolean active) {}
