package com.smartdroneinspection.dashboard.api;

import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse;
import com.smartdroneinspection.dashboard.service.DashboardAnalyticsService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final DashboardAnalyticsService analyticsService;

  public DashboardController(DashboardAnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/analytics")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CUSTOMER', 'INSPECTOR', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<DashboardAnalyticsResponse> getAnalytics(@AuthenticationPrincipal Jwt jwt) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    return ApiResponse.success(analyticsService.getAnalytics(actorId));
  }
}
