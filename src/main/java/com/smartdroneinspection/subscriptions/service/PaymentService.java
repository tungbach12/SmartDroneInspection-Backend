package com.smartdroneinspection.subscriptions.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.api.dto.CapturePayPalOrderRequest;
import com.smartdroneinspection.subscriptions.api.dto.PayPalOrderResponse;
import com.smartdroneinspection.subscriptions.api.dto.PaymentTransactionResponse;
import com.smartdroneinspection.subscriptions.domain.ContractTerms;
import com.smartdroneinspection.subscriptions.domain.PaymentTransaction;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import com.smartdroneinspection.subscriptions.repository.PaymentTransactionRepository;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles enterprise subscription payments through the configured gateway.
 *
 * <p>Two rules govern everything here. The browser never states that money moved: it names an
 * order, and the platform asks the gateway what actually happened to that order. And a subscription
 * becomes active only from a capture the gateway confirms, whose settled amount and currency match
 * the contracted price.
 */
@Service
@Transactional
public class PaymentService {

  private static final String SETTLEMENT_CURRENCY = "USD";
  private static final String CAPTURE_STATUS_COMPLETED = "COMPLETED";

  private final PaymentGateway gateway;
  private final PaymentTransactionRepository paymentTransactions;
  private final SubscriptionRepository subscriptions;
  private final UserAccess users;

  public PaymentService(
      PaymentGateway gateway,
      PaymentTransactionRepository paymentTransactions,
      SubscriptionRepository subscriptions,
      UserAccess users) {
    this.gateway = gateway;
    this.paymentTransactions = paymentTransactions;
    this.subscriptions = subscriptions;
    this.users = users;
  }

  /**
   * Creates a gateway order for the contracted price of a subscription.
   *
   * <p>The amount comes from the subscription the platform priced itself, never from the caller.
   */
  public PayPalOrderResponse createPayPalOrder(UUID actorUserId, UUID subscriptionId) {
    Subscription subscription = requireOrgScopedSubscription(actorUserId, subscriptionId);

    if (subscription.getStatus() == Subscription.Status.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SUBSCRIPTION_ALREADY_ACTIVE", "Subscription is already active");
    }
    if (subscription.getAgreedAmountUsd() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SUBSCRIPTION_PRICE_NOT_APPROVED",
          "A platform administrator must approve the contract price before online payment.");
    }

    BigDecimal amount = contractedAmount(subscription);
    PaymentGateway.PaymentGatewayOrder order =
        invokeGateway(
            () ->
                gateway.createOrder(
                    amount,
                    SETTLEMENT_CURRENCY,
                    "SmartDroneInspection ENTERPRISE subscription, "
                        + subscription.getBillingPeriodMonths()
                        + " month term"));

    // The order is recorded before the payer is sent to the provider, so a capture that arrives
    // later — through a webhook, for instance — can be tied back to this subscription.
    paymentTransactions.save(
        PaymentTransaction.recordPendingOrder(
            subscription.getId(), order.orderId(), amount, SETTLEMENT_CURRENCY));

    return new PayPalOrderResponse(
        order.orderId(), order.status(), amount, SETTLEMENT_CURRENCY, order.approvalUrl());
  }

  /**
   * Captures a gateway order and activates the subscription when the capture is confirmed.
   *
   * <p>The request identifies the order to capture. The capture id, the settled amount, the
   * currency, and the payer all come back from the gateway and override whatever the caller sent.
   */
  public PaymentTransactionResponse capturePayPalOrder(
      UUID actorUserId, CapturePayPalOrderRequest request) {
    Subscription subscription = requireOrgScopedSubscription(actorUserId, request.subscriptionId());

    PaymentTransaction order =
        paymentTransactions
            .findByPaypalOrderId(request.paypalOrderId())
            .filter(tx -> tx.getSubscriptionId().equals(subscription.getId()))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_ORDER_NOT_FOUND",
                        "No payment order matches this subscription"));
    if (order.getStatus() == PaymentTransaction.Status.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_PAYMENT_CAPTURE", "This order has already been settled");
    }

    PaymentGateway.Capture capture =
        invokeGateway(() -> gateway.captureOrder(request.paypalOrderId()));

    if (capture.captureId() == null || capture.captureId().isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "PAYMENT_CAPTURE_NOT_COMPLETED",
          "The payment provider did not report a capture for this order");
    }
    if (!CAPTURE_STATUS_COMPLETED.equalsIgnoreCase(capture.status())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_CAPTURE_NOT_COMPLETED",
          "The payment provider reported the capture as " + capture.status());
    }
    if (!request.paypalOrderId().equals(capture.orderId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_ORDER_MISMATCH",
          "The captured order does not match the requested order");
    }

    BigDecimal settledAmount = parseAmount(capture.amount());
    if (settledAmount == null || settledAmount.compareTo(contractedAmount(subscription)) != 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_AMOUNT_MISMATCH",
          "The settled amount does not match the contracted amount");
    }
    if (capture.currency() != null
        && !SETTLEMENT_CURRENCY.equalsIgnoreCase(capture.currency().trim())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_AMOUNT_MISMATCH",
          "The settled currency does not match the contracted currency");
    }

    if (paymentTransactions.findByPaypalCaptureId(capture.captureId()).isPresent()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DUPLICATE_PAYMENT_CAPTURE",
          "This capture has already been processed");
    }

    if (!capture.orderId().equals(order.getPaypalOrderId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_ORDER_MISMATCH",
          "The captured order does not match the requested order");
    }

    order.markCompleted(
        capture.captureId(), settledAmount, SETTLEMENT_CURRENCY, capture.payerEmail());
    PaymentTransaction transaction = order;
    PaymentTransaction stored = paymentTransactions.save(transaction);

    activateSubscription(subscription, capture.captureId(), settledAmount, actorUserId);

    return toResponse(stored);
  }

  /**
   * Applies a capture the gateway reported through its webhook.
   *
   * <p>The subscription is located by the gateway order id, so a notification is bound to the order
   * the platform itself created.
   */
  @Transactional
  public void applyCapturedOrder(String orderId, String captureId, BigDecimal amount) {
    PaymentTransaction order =
        paymentTransactions
            .findByPaypalOrderId(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_ORDER_NOT_FOUND",
                        "No payment order matches the notified order"));

    if (paymentTransactions.findByPaypalCaptureId(captureId).isPresent()
        || order.getStatus() == PaymentTransaction.Status.COMPLETED) {
      return; // Already settled; the gateway retries notifications.
    }

    Subscription subscription =
        subscriptions
            .findById(order.getSubscriptionId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription matches the notified order"));

    BigDecimal contracted = contractedAmount(subscription);
    if (amount == null || amount.compareTo(contracted) != 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_AMOUNT_MISMATCH",
          "The notified amount does not match the contracted amount");
    }

    order.markCompleted(captureId, amount, SETTLEMENT_CURRENCY, null);
    paymentTransactions.save(order);

    activateSubscription(subscription, captureId, amount, null);
  }

  @Transactional(readOnly = true)
  public List<PaymentTransactionResponse> listTransactions(UUID actorUserId, UUID subscriptionId) {
    Subscription subscription = requireOrgScopedSubscription(actorUserId, subscriptionId);
    return paymentTransactions
        .findAllBySubscriptionIdOrderByCapturedAtDesc(subscription.getId())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * Activates the subscription and expires the previous active term.
   *
   * <p>The database holds a partial unique index allowing only one active subscription per
   * organization, so the previous term is closed before the new one opens.
   */
  private void activateSubscription(
      Subscription subscription, String captureId, BigDecimal amount, UUID actorUserId) {
    Instant startsAt = Instant.now();

    subscriptions
        .findFirstByOrganizationIdAndStatus(
            subscription.getOrganizationId(), Subscription.Status.ACTIVE)
        .ifPresent(
            previousActive -> {
              if (!previousActive.getId().equals(subscription.getId())) {
                previousActive.expire(startsAt);
                subscriptions.saveAndFlush(previousActive);
              }
            });

    Instant endsAt =
        startsAt
            .atZone(ZoneOffset.UTC)
            .plusMonths(subscription.getBillingPeriodMonths())
            .toInstant();

    subscription.confirmPayment("PAYPAL:" + captureId, startsAt, endsAt, amount, actorUserId);
    subscriptions.saveAndFlush(subscription);
  }

  /** The price the platform contracted for, independent of what any caller supplies. */
  private <T> T invokeGateway(java.util.function.Supplier<T> request) {
    try {
      return request.get();
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "PAYMENT_GATEWAY_UNAVAILABLE",
          "Payment provider request failed. Please retry or use corporate wire transfer.");
    }
  }

  private BigDecimal contractedAmount(Subscription subscription) {
    BigDecimal amount =
        subscription.getAgreedAmountUsd() != null
            ? subscription.getAgreedAmountUsd()
            : ContractTerms.calculateStandardPrice(subscription.getBillingPeriodMonths())
                .totalPriceUsd();
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal parseAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Subscription requireOrgScopedSubscription(UUID actorUserId, UUID subscriptionId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorUserId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Active user session required"));

    Subscription subscription =
        subscriptions
            .findById(subscriptionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "Subscription not found: " + subscriptionId));

    if (!actor.hasRole(Roles.ADMIN)
        && (!actor.hasRole(Roles.ORG_ADMIN)
            || !subscription.getOrganizationId().equals(actor.organizationId()))) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "You are not authorized to access this subscription's payments");
    }

    return subscription;
  }

  private PaymentTransactionResponse toResponse(PaymentTransaction transaction) {
    return new PaymentTransactionResponse(
        transaction.getId(),
        transaction.getSubscriptionId(),
        transaction.getPaypalOrderId(),
        transaction.getPaypalCaptureId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getStatus(),
        transaction.getPayerEmail(),
        transaction.getCapturedAt());
  }
}
