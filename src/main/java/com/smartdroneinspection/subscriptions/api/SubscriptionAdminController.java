package com.smartdroneinspection.subscriptions.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.subscriptions.api.dto.request.ActivateSubscriptionRequest;
import com.smartdroneinspection.subscriptions.api.dto.response.SubscriptionResponse;
import com.smartdroneinspection.subscriptions.service.SubscriptionActivationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform administration of organization subscription entitlement. */
@RestController
@RequestMapping("/api/v1/platform/organizations/{organizationId}/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionAdminController {

  private final SubscriptionActivationService subscriptions;

  public SubscriptionAdminController(SubscriptionActivationService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SubscriptionResponse> activate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID organizationId,
      @Valid @RequestBody ActivateSubscriptionRequest request) {
    return ApiResponse.success(
        subscriptions.activate(UUID.fromString(jwt.getSubject()), organizationId, request));
  }
}
