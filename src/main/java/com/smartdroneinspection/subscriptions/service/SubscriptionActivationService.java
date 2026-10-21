package com.smartdroneinspection.subscriptions.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.api.dto.request.ActivateSubscriptionRequest;
import com.smartdroneinspection.subscriptions.api.dto.response.SubscriptionResponse;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Platform-admin subscription activation. Consuming tenant modules use the read-only entitlement
 * port.
 */
@Service
public class SubscriptionActivationService {

  private final SubscriptionRepository subscriptions;
  private final UserAccess userAccess;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;

  public SubscriptionActivationService(
      SubscriptionRepository subscriptions,
      UserAccess userAccess,
      ObjectMapper objectMapper,
      JdbcTemplate jdbcTemplate) {
    this.subscriptions = subscriptions;
    this.userAccess = userAccess;
    this.objectMapper = objectMapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public SubscriptionResponse activate(
      UUID actorId, UUID organizationId, ActivateSubscriptionRequest request) {
    var actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Platform Admin must be active"));
    if (!actor.hasRole("ADMIN") || actor.organizationId() != null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Platform ADMIN role is required");
    }
    if (!userAccess.organizationExists(organizationId)) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization was not found");
    }
    Instant now = Instant.now();
    subscriptions
        .findActiveForUpdate(organizationId)
        .ifPresent(
            current -> {
              if (current.covers(now)) {
                throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SUBSCRIPTION_ALREADY_ACTIVE",
                    "Organization already has an active subscription");
              }
              if (current.hasEnded(now)) {
                expireEndedSubscription(organizationId, actorId, current, now);
              }
            });

    String termsSnapshot = serializeTerms(request);
    Subscription subscription =
        Subscription.activate(
            organizationId,
            request.billingPeriodMonths(),
            request.startsAt(),
            request.endsAt(),
            request.externalReference(),
            termsSnapshot,
            actorId);
    try {
      subscription = subscriptions.saveAndFlush(subscription);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SUBSCRIPTION_ALREADY_ACTIVE",
          "Organization already has an active subscription");
    }

    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (organization_id, actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, reason, safe_metadata, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """,
        organizationId,
        actorId,
        "SUBSCRIPTION_ACTIVATED",
        "SUBSCRIPTION",
        subscription.getId(),
        null,
        subscription.getStatus().name(),
        request.reason().trim(),
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "planCode", subscription.getPlanCode(),
                "billingPeriodMonths", subscription.getBillingPeriodMonths(),
                "externalReference", subscription.getExternalReference())),
        java.sql.Timestamp.from(subscription.getConfirmedAt()));
    return SubscriptionResponse.from(subscription);
  }

  private void expireEndedSubscription(
      UUID organizationId, UUID actorId, Subscription subscription, Instant expiredAt) {
    int updated =
        jdbcTemplate.update(
            "UPDATE subscriptions SET status = 'EXPIRED', updated_at = ?, row_version = row_version + 1 WHERE id = ? AND status = 'ACTIVE' AND ends_at <= ?",
            java.sql.Timestamp.from(expiredAt),
            subscription.getId(),
            java.sql.Timestamp.from(expiredAt));
    if (updated != 1) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SUBSCRIPTION_STATE_CHANGED",
          "Subscription state changed while it was being activated");
    }
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (organization_id, actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, reason, safe_metadata, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """,
        organizationId,
        actorId,
        "SUBSCRIPTION_EXPIRED",
        "SUBSCRIPTION",
        subscription.getId(),
        Subscription.Status.ACTIVE.name(),
        Subscription.Status.EXPIRED.name(),
        "Subscription validity window ended before reactivation",
        objectMapper.writeValueAsString(
            java.util.Map.of("externalReference", subscription.getExternalReference())),
        java.sql.Timestamp.from(expiredAt));
  }

  private String serializeTerms(ActivateSubscriptionRequest request) {
    try {
      return objectMapper.writeValueAsString(
          request.termsSnapshot() == null ? java.util.Map.of() : request.termsSnapshot());
    } catch (JacksonException exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "SUBSCRIPTION_TERMS_INVALID",
          "Subscription terms could not be serialized");
    }
  }
}
