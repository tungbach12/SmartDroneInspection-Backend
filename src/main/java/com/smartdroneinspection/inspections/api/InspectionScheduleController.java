package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.CreateRecurringScheduleRequest;
import com.smartdroneinspection.inspections.api.dto.request.SkipOccurrenceRequest;
import com.smartdroneinspection.inspections.api.dto.response.RecurringScheduleDetailResponse;
import com.smartdroneinspection.inspections.api.dto.response.RecurringSchedulePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.RecurringScheduleResponse;
import com.smartdroneinspection.inspections.api.dto.response.ScheduleOccurrenceResponse;
import com.smartdroneinspection.inspections.service.RecurringInspectionScheduleService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-schedules")
public class InspectionScheduleController {

  private final RecurringInspectionScheduleService scheduleService;

  public InspectionScheduleController(RecurringInspectionScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @PostMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<RecurringScheduleResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateRecurringScheduleRequest request) {
    return ApiResponse.success(scheduleService.create(UUID.fromString(jwt.getSubject()), request));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CUSTOMER', 'INSPECTOR')")
  public ApiResponse<RecurringSchedulePageResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) UUID assetId,
      @RequestParam(required = false) String status) {
    return ApiResponse.success(
        scheduleService.list(UUID.fromString(jwt.getSubject()), assetId, status, page, size));
  }

  @GetMapping("/{scheduleId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CUSTOMER', 'INSPECTOR')")
  public ApiResponse<RecurringScheduleDetailResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID scheduleId) {
    return ApiResponse.success(scheduleService.get(UUID.fromString(jwt.getSubject()), scheduleId));
  }

  @PostMapping("/{scheduleId}/pause")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ApiResponse<RecurringScheduleResponse> pause(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID scheduleId) {
    return ApiResponse.success(
        scheduleService.pause(UUID.fromString(jwt.getSubject()), scheduleId));
  }

  @PostMapping("/{scheduleId}/resume")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ApiResponse<RecurringScheduleResponse> resume(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID scheduleId) {
    return ApiResponse.success(
        scheduleService.resume(UUID.fromString(jwt.getSubject()), scheduleId));
  }

  @PostMapping("/{scheduleId}/end")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ApiResponse<RecurringScheduleResponse> end(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID scheduleId) {
    return ApiResponse.success(scheduleService.end(UUID.fromString(jwt.getSubject()), scheduleId));
  }

  @PostMapping("/{scheduleId}/occurrences/{occurrenceId}/retry")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ApiResponse<ScheduleOccurrenceResponse> retryOccurrence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID scheduleId,
      @PathVariable UUID occurrenceId) {
    return ApiResponse.success(
        scheduleService.retryOccurrence(
            UUID.fromString(jwt.getSubject()), scheduleId, occurrenceId));
  }

  @PostMapping("/{scheduleId}/occurrences/{occurrenceId}/skip")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ApiResponse<ScheduleOccurrenceResponse> skipOccurrence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID scheduleId,
      @PathVariable UUID occurrenceId,
      @Valid @RequestBody SkipOccurrenceRequest request) {
    return ApiResponse.success(
        scheduleService.skipOccurrence(
            UUID.fromString(jwt.getSubject()), scheduleId, occurrenceId, request));
  }
}
