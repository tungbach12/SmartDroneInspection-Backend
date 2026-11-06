package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateChecklistTemplateRequest(
    @NotBlank @Size(max = 32) String templateType,
    UUID categoryId,
    @NotBlank @Size(max = 300) String label,
    Integer sortOrder) {}
