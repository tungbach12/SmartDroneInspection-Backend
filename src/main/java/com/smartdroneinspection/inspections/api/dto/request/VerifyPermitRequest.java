package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyPermitRequest(@NotBlank String flightPermitReference) {}
