package com.smartdroneinspection.subscriptions.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.subscriptions.api.dto.ConfirmPaymentRequest;
import com.smartdroneinspection.subscriptions.api.dto.SubscriptionResponse;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/subscriptions")
public class PlatformSubscriptionController {

  private final SubscriptionService subscriptionService;

  public PlatformSubscriptionController(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<List<SubscriptionResponse>> list(
      @RequestParam(required = false) Subscription.Status status) {
    return ApiResponse.success(subscriptionService.listAllSubscriptions(status));
  }

  @GetMapping("/{subscriptionId}")
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<SubscriptionResponse> getDetails(@PathVariable UUID subscriptionId) {
    return ApiResponse.success(subscriptionService.getSubscriptionDetails(subscriptionId));
  }

  @PostMapping("/{subscriptionId}/confirm-payment")
  @PreAuthorize("hasRole('" + Roles.MANAGER + "')")
  public ApiResponse<SubscriptionResponse> confirmPayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID subscriptionId,
      @Valid @RequestBody ConfirmPaymentRequest request) {
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    SubscriptionResponse response =
        subscriptionService.confirmPayment(actorUserId, subscriptionId, request);
    return ApiResponse.success("Payment confirmed and subscription activated", response);
  }
}
