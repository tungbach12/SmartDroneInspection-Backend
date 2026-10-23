package com.smartdroneinspection.subscriptions.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ConfirmPaymentRequest(
    @NotBlank(message = "External payment or bank invoice reference is required")
        @Size(max = 128, message = "External reference must not exceed 128 characters")
        String externalReference,
    Instant startsAt,
    Instant endsAt,
    BigDecimal agreedAmountUsd,
    String adminNotes) {

  public ConfirmPaymentRequest(String externalReference, Instant startsAt, Instant endsAt) {
    this(externalReference, startsAt, endsAt, null, null);
  }
}
