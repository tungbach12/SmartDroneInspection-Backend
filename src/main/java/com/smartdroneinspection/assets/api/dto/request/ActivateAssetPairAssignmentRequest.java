package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateAssetPairAssignmentRequest(@NotBlank @Size(max = 2000) String reason) {}
