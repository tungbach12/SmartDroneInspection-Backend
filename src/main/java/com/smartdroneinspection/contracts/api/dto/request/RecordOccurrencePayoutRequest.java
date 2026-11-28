package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordOccurrencePayoutRequest(
    @NotBlank @Size(max = 255) String payoutReference, @Size(max = 2000) String note) {}
