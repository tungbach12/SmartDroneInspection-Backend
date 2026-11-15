package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectInspectorOnboardingRequest(@NotBlank @Size(max = 1000) String reason) {}
