package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.UpdatePlatformAiSettingsRequest;
import com.smartdroneinspection.inspections.api.dto.response.PlatformAiAnalyticsResponse;
import com.smartdroneinspection.inspections.service.PlatformAiSettingsService;
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
@RequestMapping("/api/v1/inspections/ai-settings")
public class PlatformAiSettingsController {

  private final PlatformAiSettingsService aiSettingsService;

  public PlatformAiSettingsController(PlatformAiSettingsService aiSettingsService) {
    this.aiSettingsService = aiSettingsService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INSPECTOR')")
  public ApiResponse<PlatformAiAnalyticsResponse> getAiSettingsAndAnalytics() {
    return ApiResponse.success(aiSettingsService.getAnalytics());
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PlatformAiAnalyticsResponse> updateAiSettings(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdatePlatformAiSettingsRequest request) {
    return ApiResponse.success(aiSettingsService.updateSettings(actorId(jwt), request));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
