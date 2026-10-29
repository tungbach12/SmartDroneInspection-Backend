package com.smartdroneinspection.subscriptions.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.api.dto.ConfirmPaymentRequest;
import com.smartdroneinspection.subscriptions.api.dto.ContractTermsResponse;
import com.smartdroneinspection.subscriptions.api.dto.CreateSubscriptionRequest;
import com.smartdroneinspection.subscriptions.api.dto.PaymentInstructionsResponse;
import com.smartdroneinspection.subscriptions.api.dto.SubscriptionCurrentResponse;
import com.smartdroneinspection.subscriptions.api.dto.SubscriptionResponse;
import com.smartdroneinspection.subscriptions.domain.ContractTerms;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class SubscriptionService {

  private final SubscriptionRepository subscriptionRepository;
  private final UserAccess userAccess;
  private final ObjectMapper objectMapper;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      UserAccess userAccess,
      ObjectMapper objectMapper) {
    this.subscriptionRepository = subscriptionRepository;
    this.userAccess = userAccess;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public SubscriptionCurrentResponse getCurrentSubscription(UUID actorUserId) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorUserId);
    UUID organizationId = actor.organizationId();

    List<Subscription> historyEntities =
        subscriptionRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId);

    String organizationName =
        userAccess.findOrganizationName(organizationId).orElse("Organization");

    List<SubscriptionResponse> history =
        historyEntities.stream().map(s -> toResponse(s, organizationName)).toList();

    Subscription current =
        historyEntities.stream()
            .filter(s -> s.getStatus() == Subscription.Status.ACTIVE)
            .findFirst()
            .or(
                () ->
                    historyEntities.stream()
                        .filter(s -> s.getStatus() == Subscription.Status.PENDING)
                        .findFirst())
            .or(() -> historyEntities.stream().findFirst())
            .orElse(null);

    SubscriptionResponse currentResponse =
        current != null ? toResponse(current, organizationName) : null;

    int billingPeriod = current != null ? current.getBillingPeriodMonths() : 12;
    var pricingCalc = ContractTerms.calculateStandardPrice(billingPeriod);
    BigDecimal amountUsd =
        (current != null && current.getAgreedAmountUsd() != null)
            ? current.getAgreedAmountUsd()
            : pricingCalc.totalPriceUsd();

    String organizationCode =
        userAccess.findOrganizationCode(organizationId).orElse(organizationName);

    PaymentInstructionsResponse instructions =
        PaymentInstructionsResponse.forOrganization(organizationCode, billingPeriod, amountUsd);

    return new SubscriptionCurrentResponse(
        currentResponse, history, ContractTermsResponse.standard(), instructions);
  }

  public SubscriptionResponse requestSubscription(
      UUID actorUserId, CreateSubscriptionRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorUserId);
    UUID organizationId = actor.organizationId();

    if (request.billingPeriodMonths() == null
        || request.billingPeriodMonths() < 1
        || request.billingPeriodMonths() > 120) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_BILLING_PERIOD",
          "Billing period must be between 1 and 120 months");
    }

    if (!Boolean.TRUE.equals(request.agreeToTerms())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "TERMS_AGREEMENT_REQUIRED",
          "You must review and agree to the enterprise terms of service");
    }

    // Clean up any existing pending requests for this organization
    subscriptionRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .filter(s -> s.getStatus() == Subscription.Status.PENDING)
        .forEach(
            pending -> {
              pending.cancel("Superseded by new subscription request");
              subscriptionRepository.save(pending);
            });

    String termsVersion =
        (request.termsVersion() != null && !request.termsVersion().isBlank())
            ? request.termsVersion().trim()
            : ContractTerms.VERSION;

    var pricingCalc = ContractTerms.calculateStandardPrice(request.billingPeriodMonths());
    String termsSnapshotJson =
        buildTermsSnapshotJson(
            actor,
            request.billingPeriodMonths(),
            termsVersion,
            pricingCalc,
            request.proposedPriceUsd(),
            request.negotiationNotes());

    Subscription subscription =
        Subscription.requestEnterprise(
            organizationId,
            request.billingPeriodMonths(),
            request.proposedPriceUsd(),
            termsSnapshotJson);

    Subscription saved = subscriptionRepository.save(subscription);
    String organizationName =
        userAccess.findOrganizationName(organizationId).orElse("Organization");
    return toResponse(saved, organizationName);
  }

  @Transactional(readOnly = true)
  public List<SubscriptionResponse> listAllSubscriptions(Subscription.Status statusFilter) {
    List<Subscription> list =
        statusFilter != null
            ? subscriptionRepository.findByStatusOrderByCreatedAtDesc(statusFilter)
            : subscriptionRepository.findAllByOrderByCreatedAtDesc();

    Map<UUID, String> orgNameCache = new LinkedHashMap<>();
    return list.stream()
        .map(
            s -> {
              String name =
                  orgNameCache.computeIfAbsent(
                      s.getOrganizationId(),
                      id -> userAccess.findOrganizationName(id).orElse("Organization"));
              return toResponse(s, name);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public SubscriptionResponse getSubscriptionDetails(UUID subscriptionId) {
    Subscription s = findSubscriptionOrThrow(subscriptionId);
    String orgName = userAccess.findOrganizationName(s.getOrganizationId()).orElse("Organization");
    return toResponse(s, orgName);
  }

  public SubscriptionResponse confirmPayment(
      UUID adminUserId, UUID subscriptionId, ConfirmPaymentRequest request) {
    requirePlatformManager(adminUserId);

    if (request.externalReference() == null || request.externalReference().isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EXTERNAL_REFERENCE_REQUIRED",
          "Bank wire statement or invoice reference is required");
    }

    Subscription target = findSubscriptionOrThrow(subscriptionId);

    if (target.getStatus() == Subscription.Status.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SUBSCRIPTION_ALREADY_ACTIVE",
          "This subscription is already active");
    }

    // Enforce PostgreSQL partial unique index uq_subscriptions_active_organization:
    // Expire any existing active subscription for this organization before activating the new one.
    Instant effectiveStartsAt = request.startsAt() != null ? request.startsAt() : Instant.now();

    subscriptionRepository
        .findFirstByOrganizationIdAndStatus(target.getOrganizationId(), Subscription.Status.ACTIVE)
        .ifPresent(
            previousActive -> {
              if (!previousActive.getId().equals(target.getId())) {
                previousActive.expire(effectiveStartsAt);
                subscriptionRepository.saveAndFlush(previousActive);
              }
            });

    Instant effectiveEndsAt =
        request.endsAt() != null
            ? request.endsAt()
            : effectiveStartsAt
                .atZone(ZoneOffset.UTC)
                .plusMonths(target.getBillingPeriodMonths())
                .toInstant();

    if (!effectiveEndsAt.isAfter(effectiveStartsAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_DATE_RANGE",
          "End date must be strictly after start date");
    }

    BigDecimal finalAgreedAmount =
        request.agreedAmountUsd() != null
            ? request.agreedAmountUsd()
            : (target.getAgreedAmountUsd() != null
                ? target.getAgreedAmountUsd()
                : ContractTerms.calculateStandardPrice(target.getBillingPeriodMonths())
                    .totalPriceUsd());

    target.confirmPayment(
        request.externalReference(),
        effectiveStartsAt,
        effectiveEndsAt,
        finalAgreedAmount,
        adminUserId);

    Subscription saved = subscriptionRepository.saveAndFlush(target);
    String orgName =
        userAccess.findOrganizationName(saved.getOrganizationId()).orElse("Organization");
    return toResponse(saved, orgName);
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID userId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Active user session required"));
    if (!user.hasRole(Roles.CUSTOMER) || user.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Organization Manager authority required");
    }
    return user;
  }

  private UserAccess.ActiveUser requirePlatformManager(UUID userId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "Active platform manager session required"));
    if (!user.hasRole(Roles.MANAGER) || user.organizationId() != null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Platform Manager authority required");
    }
    return user;
  }

  private Subscription findSubscriptionOrThrow(UUID subscriptionId) {
    return subscriptionRepository
        .findById(subscriptionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "SUBSCRIPTION_NOT_FOUND",
                    "Subscription not found: " + subscriptionId));
  }

  private String buildTermsSnapshotJson(
      UserAccess.ActiveUser actor,
      int billingPeriodMonths,
      String termsVersion,
      ContractTerms.StandardPriceCalculation pricingCalc,
      BigDecimal proposedPriceUsd,
      String negotiationNotes) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("termsVersion", termsVersion);
    map.put("agreedAt", Instant.now().toString());
    map.put("agreedByUserId", actor.id().toString());
    map.put("agreedByUserEmail", Objects.toString(actor.email(), ""));
    map.put("agreedByFullName", Objects.toString(actor.fullName(), ""));
    map.put("planCode", ContractTerms.PLAN_CODE);
    map.put("billingPeriodMonths", billingPeriodMonths);
    map.put("standardMonthlyPriceUsd", pricingCalc.monthlyPriceUsd());
    map.put("standardTotalPriceUsd", pricingCalc.totalPriceUsd());
    map.put("standardSavingsPercent", pricingCalc.savingsPercent());
    if (proposedPriceUsd != null) {
      map.put("proposedPriceUsd", proposedPriceUsd);
    }
    if (negotiationNotes != null && !negotiationNotes.isBlank()) {
      map.put("negotiationNotes", negotiationNotes.trim());
    }
    map.put("clauses", ContractTerms.CLAUSES);
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JacksonException e) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "JSON_ERROR", "Failed to build terms snapshot");
    }
  }

  private SubscriptionResponse toResponse(Subscription s, String organizationName) {
    JsonNode snapshotNode = null;
    if (s.getTermsSnapshot() != null && !s.getTermsSnapshot().isBlank()) {
      try {
        snapshotNode = objectMapper.readTree(s.getTermsSnapshot());
      } catch (JacksonException ignored) {
        // Leave snapshotNode as null if unparseable
      }
    }
    return new SubscriptionResponse(
        s.getId(),
        s.getOrganizationId(),
        organizationName,
        s.getPlanCode(),
        s.getBillingPeriodMonths(),
        s.getStatus(),
        s.getStartsAt(),
        s.getEndsAt(),
        s.getExternalReference(),
        s.getAgreedAmountUsd(),
        snapshotNode,
        s.getConfirmedByUserId(),
        s.getConfirmedAt(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }
}
