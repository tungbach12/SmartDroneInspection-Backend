package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.api.dto.request.UpdatePlatformOperationsSettingsRequest;
import com.smartdroneinspection.contracts.api.dto.response.PlatformOperationsSettingsResponse;
import com.smartdroneinspection.contracts.service.PlatformOperationsSettingsService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/operations-settings")
public class PlatformOperationsSettingsController {

  private final PlatformOperationsSettingsService operationsSettings;

  public PlatformOperationsSettingsController(
      PlatformOperationsSettingsService operationsSettings) {
    this.operationsSettings = operationsSettings;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PlatformOperationsSettingsResponse> getSettings(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(operationsSettings.getSettings(actorId(jwt)));
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PlatformOperationsSettingsResponse> updateSettings(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdatePlatformOperationsSettingsRequest request) {
    return ApiResponse.success(
        operationsSettings.updateSettings(
            actorId(jwt),
            request.assignmentAcceptanceTimeoutHours(),
            request.serviceRequestLeadDays()));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
