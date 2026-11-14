package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOccurrenceRefundRequest(@NotBlank @Size(max = 2000) String reason) {}
