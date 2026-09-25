package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description) {}
