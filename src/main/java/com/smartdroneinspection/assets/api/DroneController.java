package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneServiceabilityRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneSelectionResponse;
import com.smartdroneinspection.assets.service.DroneService;
import com.smartdroneinspection.shared.api.ApiResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drones")
public class DroneController {

  private final DroneService drones;

  public DroneController(DroneService drones) {
    this.drones = drones;
  }

  @PostMapping
  @PreAuthorize("hasRole('ORG_MANAGER')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DroneResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateDroneRequest request) {
    return ApiResponse.success(drones.create(subject(jwt), request));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'PLATFORM_MANAGER')")
  public ApiResponse<List<DroneResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(drones.list(subject(jwt)));
  }

  /** Serviceable airframes of the caller's organization; no permit or readiness claim is made. */
  @GetMapping("/selection")
  @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'PLATFORM_MANAGER')")
  public ApiResponse<List<DroneSelectionResponse>> selection(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(drones.selection(subject(jwt)));
  }

  @GetMapping("/{droneId}")
  @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'PLATFORM_MANAGER')")
  public ApiResponse<DroneResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID droneId) {
    return ApiResponse.success(drones.get(subject(jwt), droneId));
  }

  @PatchMapping("/{droneId}/serviceability")
  @PreAuthorize("hasRole('ORG_MANAGER')")
  public ApiResponse<DroneResponse> updateServiceability(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID droneId,
      @Valid @RequestBody UpdateDroneServiceabilityRequest request) {
    return ApiResponse.success(drones.updateServiceability(subject(jwt), droneId, request));
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
