package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChecklistTemplateRequest(
    @NotBlank @Size(max = 300) String label, Integer sortOrder, Boolean active) {}
