package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDroneDocumentConfigRequest(
    @NotBlank
        @Size(max = 64)
        @Pattern(
            regexp = "^[A-Za-z0-9_]+$",
            message = "Code must contain only letters, numbers, and underscores")
        String code,
    @NotBlank @Size(max = 255) String titleVi,
    @NotBlank @Size(max = 255) String titleEn,
    @Size(max = 1000) String descriptionVi,
    @Size(max = 1000) String descriptionEn,
    Boolean isRequired,
    Boolean isActive,
    Integer displayOrder) {}
