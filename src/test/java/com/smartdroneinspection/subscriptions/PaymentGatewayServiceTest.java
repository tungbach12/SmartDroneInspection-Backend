package com.smartdroneinspection.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.api.dto.CapturePayPalOrderRequest;
import com.smartdroneinspection.subscriptions.domain.PaymentTransaction;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway.PaymentGatewayOrder;
import com.smartdroneinspection.subscriptions.repository.PaymentTransactionRepository;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import com.smartdroneinspection.subscriptions.service.PaymentService;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The browser is never allowed to assert that money moved. Payment evidence comes from the gateway,
 * and a subscription becomes active only from a capture the backend itself obtained.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentGatewayServiceTest {

  @Mock PaymentGateway gateway;
  @Mock PaymentTransactionRepository paymentTransactions;
  @Mock SubscriptionRepository subscriptions;
  @Mock UserAccess users;

  @InjectMocks PaymentService service;

  private UUID actorId;
  private UUID organizationId;
  private UUID subscriptionId;
  private Subscription subscription;

  @BeforeEach
  void setUp() {
    actorId = UUID.randomUUID();
    organizationId = UUID.randomUUID();
    subscriptionId = UUID.randomUUID();
    subscription =
        Subscription.requestEnterprise(
            organizationId, 12, BigDecimal.valueOf(10200), "{\"termsVersion\":\"2026.1\"}");
    org.springframework.test.util.ReflectionTestUtils.setField(subscription, "id", subscriptionId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        subscription, "agreedAmountUsd", BigDecimal.valueOf(10200));
    when(users.findActiveUser(actorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    actorId, Set.of("CUSTOMER"), organizationId, "Alice Admin", "a@x.test")));
    when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
    when(paymentTransactions.findByPaypalOrderId("ORDER-1"))
        .thenReturn(
            Optional.of(
                PaymentTransaction.recordPendingOrder(
                    subscriptionId, "ORDER-1", BigDecimal.valueOf(10200), "USD")));
    when(paymentTransactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void webhookCaptureIsIdempotentForSameAndSubsequentCaptureOnCompletedOrder() {
    PaymentTransaction completedOrder =
        PaymentTransaction.recordCompletedCapture(
            subscriptionId,
            "ORDER-1",
            "CAPTURE-OLD",
            BigDecimal.valueOf(10200),
            "USD",
            null,
            Instant.now());
    when(paymentTransactions.findByPaypalOrderId("ORDER-1"))
        .thenReturn(Optional.of(completedOrder));
    when(paymentTransactions.findByPaypalCaptureId("CAPTURE-OLD"))
        .thenReturn(Optional.of(completedOrder));
    when(paymentTransactions.findByPaypalCaptureId("CAPTURE-NEW")).thenReturn(Optional.empty());

    service.applyCapturedOrder("ORDER-1", "CAPTURE-OLD", BigDecimal.valueOf(10200));
    service.applyCapturedOrder("ORDER-1", "CAPTURE-NEW", BigDecimal.valueOf(10200));

    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.PENDING);
    verify(paymentTransactions, never()).save(any());
    verify(subscriptions, never()).saveAndFlush(any());
  }

  @Test
  void createsOrderFromServerSidePriceAndNeverFromClientAmount() {
    when(gateway.createOrder(eq(new BigDecimal("10200.00")), eq("USD"), any()))
        .thenReturn(
            new PaymentGatewayOrder(
                "ORDER-1", "CREATED", "https://www.sandbox.paypal.com/checkout?token=ORDER-1"));

    var response = service.createPayPalOrder(actorId, subscriptionId);

    assertThat(response.paypalOrderId()).isEqualTo("ORDER-1");
    assertThat(response.amount()).isEqualByComparingTo("10200.00");
    assertThat(response.approvalUrl())
        .isEqualTo("https://www.sandbox.paypal.com/checkout?token=ORDER-1");
    verify(gateway).createOrder(eq(new BigDecimal("10200.00")), eq("USD"), any());
  }

  @Test
  void contractPreviewDoesNotPromiseUnverifiedSlaOrDataRetentionControls() {
    String clauses =
        com.smartdroneinspection.subscriptions.domain.ContractTerms.CLAUSES.stream()
            .map(com.smartdroneinspection.subscriptions.domain.ContractTerms.Clause::text)
            .collect(java.util.stream.Collectors.joining(" "));

    assertThat(clauses)
        .doesNotContain("99.98%", "SHA-256 integrity", "statutory infrastructure compliance")
        .contains(
            "Optional PayPal Orders checkout",
            "enabled and configured",
            "not a certified digital signature");
  }

  @Test
  void customerPriceProposalDoesNotBecomeApprovedContractAmount() {
    Subscription proposed =
        Subscription.requestEnterprise(
            organizationId, 12, BigDecimal.valueOf(8000), "{\"proposedPriceUsd\":8000}");

    assertThat(proposed.getAgreedAmountUsd()).isNull();
  }

  @Test
  void rejectsOrderCreationUntilPlatformApprovesTheContractPrice() {
    Subscription unapproved = Subscription.requestEnterprise(organizationId, 12, "{}");
    org.springframework.test.util.ReflectionTestUtils.setField(unapproved, "id", subscriptionId);
    when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(unapproved));

    assertThatThrownBy(() -> service.createPayPalOrder(actorId, subscriptionId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("SUBSCRIPTION_PRICE_NOT_APPROVED"));

    verify(gateway, never()).createOrder(any(), any(), any());
  }

  @Test
  void rejectsOrderCreationWhenSubscriptionIsAlreadyActive() {
    subscription.confirmPayment(
        "WIRE-1",
        Instant.now().minusSeconds(60),
        Instant.now().plusSeconds(3600),
        BigDecimal.valueOf(10200),
        actorId);

    assertThatThrownBy(() -> service.createPayPalOrder(actorId, subscriptionId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("SUBSCRIPTION_ALREADY_ACTIVE"));

    verify(gateway, never()).createOrder(any(), any(), any());
  }

  @Test
  void capturesOrderAndActivatesSubscriptionOnlyFromGatewayVerifiedCapture() {
    when(gateway.captureOrder("ORDER-1"))
        .thenReturn(
            new PaymentGateway.Capture(
                "ORDER-1", "CAPTURE-1", "COMPLETED", "10200.00", "USD", "payer@client.test"));

    var response =
        service.capturePayPalOrder(
            actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1"));

    assertThat(response.paypalCaptureId()).isEqualTo("CAPTURE-1");
    assertThat(response.status()).isEqualTo(PaymentTransaction.Status.COMPLETED);
    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.ACTIVE);
    assertThat(subscription.getExternalReference()).isEqualTo("PAYPAL:CAPTURE-1");
  }

  @Test
  void rejectsCaptureWhenGatewayReportsFailure() {
    when(gateway.captureOrder("ORDER-1"))
        .thenReturn(
            new PaymentGateway.Capture(
                "ORDER-1", null, "DECLINED", "10200.00", "USD", "payer@client.test"));

    assertThatThrownBy(
            () ->
                service.capturePayPalOrder(
                    actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("PAYMENT_CAPTURE_NOT_COMPLETED"));

    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.PENDING);
    verify(paymentTransactions, never()).save(any());
  }

  @Test
  void rejectsCaptureWhenGatewayAmountDiffersFromContractedAmount() {
    when(gateway.captureOrder("ORDER-1"))
        .thenReturn(
            new PaymentGateway.Capture("ORDER-1", "CAPTURE-1", "COMPLETED", "1.00", "USD", null));

    assertThatThrownBy(
            () ->
                service.capturePayPalOrder(
                    actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("PAYMENT_AMOUNT_MISMATCH"));

    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.PENDING);
    verify(paymentTransactions, never()).save(any());
  }

  @Test
  void rejectsCaptureWhenGatewayCurrencyDiffersFromContractedCurrency() {
    when(gateway.captureOrder("ORDER-1"))
        .thenReturn(
            new PaymentGateway.Capture(
                "ORDER-1", "CAPTURE-1", "COMPLETED", "10200.00", "EUR", null));

    assertThatThrownBy(
            () ->
                service.capturePayPalOrder(
                    actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("PAYMENT_AMOUNT_MISMATCH"));

    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.PENDING);
  }

  @Test
  void rejectsCaptureWhenTheOrderIdDoesNotMatchTheSubscriptionOrder() {
    when(gateway.captureOrder("ORDER-OTHER"))
        .thenReturn(
            new PaymentGateway.Capture(
                "ORDER-OTHER", "CAPTURE-1", "COMPLETED", "10200.00", "USD", null));

    assertThatThrownBy(
            () ->
                service.capturePayPalOrder(
                    actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-OTHER")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("PAYMENT_ORDER_NOT_FOUND"));

    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.PENDING);
    verify(paymentTransactions, never()).save(any());
  }

  @Test
  void rejectsDuplicateCaptureOfTheSameGatewayCaptureId() {
    when(paymentTransactions.findByPaypalCaptureId("CAPTURE-1"))
        .thenReturn(
            Optional.of(
                PaymentTransaction.recordCompletedCapture(
                    subscriptionId,
                    "ORDER-1",
                    "CAPTURE-1",
                    BigDecimal.valueOf(10200),
                    "USD",
                    "payer@client.test",
                    Instant.now())));
    when(gateway.captureOrder("ORDER-1"))
        .thenReturn(
            new PaymentGateway.Capture(
                "ORDER-1", "CAPTURE-1", "COMPLETED", "10200.00", "USD", "payer@client.test"));

    assertThatThrownBy(
            () ->
                service.capturePayPalOrder(
                    actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code()).isEqualTo("DUPLICATE_PAYMENT_CAPTURE"));

    assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.PENDING);
  }

  @Test
  void storesOnlyTheValuesTheGatewayReported() {
    when(gateway.captureOrder("ORDER-1"))
        .thenReturn(
            new PaymentGateway.Capture(
                "ORDER-1", "CAPTURE-1", "COMPLETED", "10200.00", "USD", "payer@client.test"));

    service.capturePayPalOrder(actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1"));

    ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
    verify(paymentTransactions).save(captor.capture());
    PaymentTransaction stored = captor.getValue();

    assertThat(stored.getPaypalCaptureId()).isEqualTo("CAPTURE-1");
    assertThat(stored.getAmount()).isEqualByComparingTo("10200.00");
    assertThat(stored.getCurrency()).isEqualTo("USD");
    assertThat(stored.getPayerEmail()).isEqualTo("payer@client.test");
  }

  @Test
  void captureRequestCarriesNoClientAssertablePaymentEvidence() {
    // The request names the order to capture and nothing else. A browser has no field in which
    // it could state a capture id, a settled amount, or a payer, so it cannot assert that money
    // moved.
    assertThat(CapturePayPalOrderRequest.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("subscriptionId", "paypalOrderId");
  }

  @Test
  void rejectsCaptureForSubscriptionOutsideTheActorOrganization() {
    Subscription foreign =
        Subscription.requestEnterprise(UUID.randomUUID(), 12, BigDecimal.valueOf(10200), "{}");
    when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                service.capturePayPalOrder(
                    actorId, new CapturePayPalOrderRequest(subscriptionId, "ORDER-1")))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("FORBIDDEN"));

    verify(gateway, never()).captureOrder(any());
  }
}
