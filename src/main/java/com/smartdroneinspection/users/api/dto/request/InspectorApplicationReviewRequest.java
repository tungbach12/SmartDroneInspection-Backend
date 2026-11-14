package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InspectorApplicationReviewRequest(@NotBlank @Size(max = 1000) String reason) {}
