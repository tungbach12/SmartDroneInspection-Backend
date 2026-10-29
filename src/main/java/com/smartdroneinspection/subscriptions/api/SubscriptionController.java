package com.smartdroneinspection.subscriptions.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.subscriptions.api.dto.CapturePayPalOrderRequest;
import com.smartdroneinspection.subscriptions.api.dto.ContractTermsResponse;
import com.smartdroneinspection.subscriptions.api.dto.CreatePayPalOrderRequest;
import com.smartdroneinspection.subscriptions.api.dto.CreateSubscriptionRequest;
import com.smartdroneinspection.subscriptions.api.dto.PayPalOrderResponse;
import com.smartdroneinspection.subscriptions.api.dto.PaymentTransactionResponse;
import com.smartdroneinspection.subscriptions.api.dto.SubscriptionCurrentResponse;
import com.smartdroneinspection.subscriptions.api.dto.SubscriptionResponse;
import com.smartdroneinspection.subscriptions.service.PaymentService;
import com.smartdroneinspection.subscriptions.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('" + Roles.CUSTOMER + "')")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;
  private final PaymentService paymentService;

  public SubscriptionController(
      SubscriptionService subscriptionService, PaymentService paymentService) {
    this.subscriptionService = subscriptionService;
    this.paymentService = paymentService;
  }

  @GetMapping("/current")
  public ApiResponse<SubscriptionCurrentResponse> getCurrent(@AuthenticationPrincipal Jwt jwt) {
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    return ApiResponse.success(subscriptionService.getCurrentSubscription(actorUserId));
  }

  @GetMapping("/terms-preview")
  public ApiResponse<ContractTermsResponse> getTermsPreview() {
    return ApiResponse.success(ContractTermsResponse.standard());
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SubscriptionResponse>> requestSubscription(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSubscriptionRequest request) {
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    SubscriptionResponse response = subscriptionService.requestSubscription(actorUserId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Subscription requested successfully", response));
  }

  /**
   * Creates a payment order for a subscription the caller owns.
   *
   * <p>The amount is the contracted price the platform computed, not a value from the request.
   */
  @PostMapping("/paypal/create-order")
  public ApiResponse<PayPalOrderResponse> createPayPalOrder(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePayPalOrderRequest request) {
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    PayPalOrderResponse order =
        paymentService.createPayPalOrder(actorUserId, request.subscriptionId());
    return ApiResponse.success("Payment order created successfully", order);
  }

  /**
   * Captures a payment order.
   *
   * <p>The request names the order only. The capture id, settled amount, currency, and payer come
   * from the payment provider, and the subscription activates only once that capture is confirmed
   * and matches the contracted amount.
   */
  @PostMapping("/paypal/capture-order")
  public ApiResponse<PaymentTransactionResponse> capturePayPalOrder(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CapturePayPalOrderRequest request) {
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    PaymentTransactionResponse transaction =
        paymentService.capturePayPalOrder(actorUserId, request);
    return ApiResponse.success("Payment captured and subscription activated", transaction);
  }

  @GetMapping("/{subscriptionId}/transactions")
  public ApiResponse<List<PaymentTransactionResponse>> listTransactions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID subscriptionId) {
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    return ApiResponse.success(paymentService.listTransactions(actorUserId, subscriptionId));
  }
}
