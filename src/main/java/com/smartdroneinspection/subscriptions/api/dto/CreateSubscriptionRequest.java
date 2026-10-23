package com.smartdroneinspection.subscriptions.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateSubscriptionRequest(
    @NotNull(message = "Billing period in months is required")
        @Min(value = 1, message = "Billing period must be at least 1 month")
        @Max(value = 120, message = "Billing period cannot exceed 120 months")
        Integer billingPeriodMonths,
    @NotNull(message = "Agreement to enterprise terms of service is required")
        @AssertTrue(message = "You must agree to the enterprise terms of service")
        Boolean agreeToTerms,
    String termsVersion,
    BigDecimal proposedPriceUsd,
    String negotiationNotes) {

  public CreateSubscriptionRequest(
      Integer billingPeriodMonths, Boolean agreeToTerms, String termsVersion) {
    this(billingPeriodMonths, agreeToTerms, termsVersion, null, null);
  }
}
