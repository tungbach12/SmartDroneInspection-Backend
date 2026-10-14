package com.smartdroneinspection.workforce.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateWorkforceStatusRequest(@NotBlank String status) {}
