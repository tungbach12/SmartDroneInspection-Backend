package com.smartdroneinspection.subscriptions.api.dto.response;

import com.smartdroneinspection.subscriptions.domain.Subscription;
import java.time.Instant;
import java.util.UUID;

/** Typed platform-admin subscription response; contains no payment credentials. */
public record SubscriptionResponse(
    UUID id,
    UUID organizationId,
    String planCode,
    int billingPeriodMonths,
    String status,
    Instant startsAt,
    Instant endsAt,
    UUID confirmedByUserId,
    Instant confirmedAt,
    String externalReference) {

  public static SubscriptionResponse from(Subscription subscription) {
    return new SubscriptionResponse(
        subscription.getId(),
        subscription.getOrganizationId(),
        subscription.getPlanCode(),
        subscription.getBillingPeriodMonths(),
        subscription.getStatus().name(),
        subscription.getStartsAt(),
        subscription.getEndsAt(),
        subscription.getConfirmedByUserId(),
        subscription.getConfirmedAt(),
        subscription.getExternalReference());
  }
}
