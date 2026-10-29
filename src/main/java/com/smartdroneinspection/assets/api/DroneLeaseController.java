package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CaptureDroneLeasePayPalRequest;
import com.smartdroneinspection.assets.api.dto.request.CreateDroneLeaseRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneLeaseResponse;
import com.smartdroneinspection.assets.api.dto.response.PayPalLeaseOrderResponse;
import com.smartdroneinspection.assets.api.dto.response.PlatformDroneResponse;
import com.smartdroneinspection.assets.service.DroneLeaseService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drone-leases")
@PreAuthorize("hasRole('" + Roles.CUSTOMER + "')")
public class DroneLeaseController {

  private final DroneLeaseService droneLeases;

  public DroneLeaseController(DroneLeaseService droneLeases) {
    this.droneLeases = droneLeases;
  }

  @GetMapping("/available-drones")
  public ApiResponse<List<PlatformDroneResponse>> listAvailableDrones(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(droneLeases.listAvailableDrones(subject(jwt)));
  }

  @GetMapping
  public ApiResponse<List<DroneLeaseResponse>> listOrganizationLeases(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(droneLeases.listOrganizationLeases(subject(jwt)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DroneLeaseResponse> requestLease(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateDroneLeaseRequest request) {
    return ApiResponse.success(
        "Drone lease requested successfully", droneLeases.requestLease(subject(jwt), request));
  }

  @PostMapping("/{leaseId}/paypal/create-order")
  public ApiResponse<PayPalLeaseOrderResponse> createPayPalOrder(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID leaseId) {
    return ApiResponse.success(
        "Payment order created successfully", droneLeases.createPayPalOrder(subject(jwt), leaseId));
  }

  @PostMapping("/{leaseId}/paypal/capture-order")
  public ApiResponse<DroneLeaseResponse> capturePayPalOrder(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID leaseId,
      @Valid @RequestBody CaptureDroneLeasePayPalRequest request) {
    return ApiResponse.success(
        "Payment captured and leased drone provisioned",
        droneLeases.capturePayPalOrder(subject(jwt), leaseId, request));
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
