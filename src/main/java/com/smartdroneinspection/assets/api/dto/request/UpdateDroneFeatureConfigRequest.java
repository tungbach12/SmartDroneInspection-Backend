package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDroneFeatureConfigRequest(
    @NotBlank @Size(max = 255) String nameVi,
    @NotBlank @Size(max = 255) String nameEn,
    @Size(max = 500) String descriptionVi,
    @Size(max = 500) String descriptionEn,
    @Size(max = 32) String category,
    Boolean isActive,
    Integer displayOrder) {}
