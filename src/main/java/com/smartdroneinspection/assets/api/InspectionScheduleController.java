package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.response.InspectionScheduleResponse;
import com.smartdroneinspection.assets.service.InspectionScheduleService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-schedules")
public class InspectionScheduleController {

  private final InspectionScheduleService schedules;

  public InspectionScheduleController(InspectionScheduleService schedules) {
    this.schedules = schedules;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
  public ApiResponse<List<InspectionScheduleResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @RequestParam UUID assetId) {
    return ApiResponse.success(schedules.listForClient(UUID.fromString(jwt.getSubject()), assetId));
  }

  @PostMapping("/{scheduleId}/pause")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<InspectionScheduleResponse> pause(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID scheduleId) {
    return ApiResponse.success(schedules.pause(UUID.fromString(jwt.getSubject()), scheduleId));
  }

  @PostMapping("/{scheduleId}/activate")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<InspectionScheduleResponse> activate(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID scheduleId) {
    return ApiResponse.success(schedules.activate(UUID.fromString(jwt.getSubject()), scheduleId));
  }
}
