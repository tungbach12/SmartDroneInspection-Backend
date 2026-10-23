package com.smartdroneinspection.subscriptions.api.dto;

import com.smartdroneinspection.subscriptions.domain.Subscription;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record SubscriptionResponse(
    UUID id,
    UUID organizationId,
    String organizationName,
    String planCode,
    int billingPeriodMonths,
    Subscription.Status status,
    Instant startsAt,
    Instant endsAt,
    String externalReference,
    BigDecimal agreedAmountUsd,
    JsonNode termsSnapshot,
    UUID confirmedByUserId,
    Instant confirmedAt,
    Instant createdAt,
    Instant updatedAt) {}
