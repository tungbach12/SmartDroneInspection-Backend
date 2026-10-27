package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkipOccurrenceRequest(@NotBlank @Size(max = 1000) String reason) {}
