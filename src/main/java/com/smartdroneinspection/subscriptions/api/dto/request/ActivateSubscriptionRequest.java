package com.smartdroneinspection.subscriptions.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/** Platform-admin decision to activate one Enterprise workspace entitlement. */
public record ActivateSubscriptionRequest(
    @NotNull Integer billingPeriodMonths,
    Instant startsAt,
    Instant endsAt,
    @NotBlank @Size(max = 128) String externalReference,
    @NotBlank @Size(max = 2000) String reason,
    Map<String, Object> termsSnapshot) {}
