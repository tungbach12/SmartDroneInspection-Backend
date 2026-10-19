package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAiFallbackPreferenceRequest(@NotNull Boolean visionFallbackEnabled) {}
