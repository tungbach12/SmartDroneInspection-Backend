package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.ConfirmDroneLeasePaymentRequest;
import com.smartdroneinspection.assets.api.dto.request.CreatePlatformDroneRequest;
import com.smartdroneinspection.assets.api.dto.request.ReclaimDroneLeaseRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdatePlatformDroneStatusRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneLeaseResponse;
import com.smartdroneinspection.assets.api.dto.response.PlatformDroneResponse;
import com.smartdroneinspection.assets.domain.enums.DroneLeaseStatus;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/drone-leases")
public class PlatformDroneLeaseController {

  private final DroneLeaseService droneLeases;

  public PlatformDroneLeaseController(DroneLeaseService droneLeases) {
    this.droneLeases = droneLeases;
  }

  @GetMapping("/pool")
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.PLATFORM_MANAGER + "')")
  public ApiResponse<List<PlatformDroneResponse>> listPool(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(droneLeases.listPool(subject(jwt)));
  }

  @PostMapping("/pool")
  @PreAuthorize("hasRole('" + Roles.PLATFORM_MANAGER + "')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PlatformDroneResponse> createPlatformDrone(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePlatformDroneRequest request) {
    return ApiResponse.success(
        "Platform drone registered in leasable pool",
        droneLeases.createPlatformDrone(subject(jwt), request));
  }

  @PatchMapping("/pool/{platformDroneId}/status")
  @PreAuthorize("hasRole('" + Roles.PLATFORM_MANAGER + "')")
  public ApiResponse<PlatformDroneResponse> updatePlatformDroneStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID platformDroneId,
      @Valid @RequestBody UpdatePlatformDroneStatusRequest request) {
    return ApiResponse.success(
        droneLeases.updatePlatformDroneStatus(subject(jwt), platformDroneId, request));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.PLATFORM_MANAGER + "')")
  public ApiResponse<List<DroneLeaseResponse>> listLeases(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) DroneLeaseStatus status) {
    return ApiResponse.success(droneLeases.listAllLeases(subject(jwt), status));
  }

  @PostMapping("/{leaseId}/confirm-payment")
  @PreAuthorize("hasRole('" + Roles.PLATFORM_MANAGER + "')")
  public ApiResponse<DroneLeaseResponse> confirmLeasePayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID leaseId,
      @Valid @RequestBody ConfirmDroneLeasePaymentRequest request) {
    return ApiResponse.success(
        "Drone lease payment confirmed and drone provisioned",
        droneLeases.confirmLeasePayment(subject(jwt), leaseId, request));
  }

  @PostMapping("/{leaseId}/reclaim")
  @PreAuthorize("hasRole('" + Roles.PLATFORM_MANAGER + "')")
  public ApiResponse<DroneLeaseResponse> reclaimLease(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID leaseId,
      @Valid @RequestBody(required = false) ReclaimDroneLeaseRequest request) {
    return ApiResponse.success(
        "Leased drone reclaimed to platform pool",
        droneLeases.reclaimLease(subject(jwt), leaseId, request));
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
