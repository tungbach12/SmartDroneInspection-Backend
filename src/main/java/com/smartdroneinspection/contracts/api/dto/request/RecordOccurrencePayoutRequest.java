package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RecordOccurrencePayoutRequest(@NotBlank String payoutReference, String note) {}
