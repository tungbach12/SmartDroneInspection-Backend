package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.AddShotItemRequest;
import com.smartdroneinspection.inspections.api.dto.request.AirspaceCheckRequest;
import com.smartdroneinspection.inspections.api.dto.request.CreateMissionPlanRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateMissionPlanRequest;
import com.smartdroneinspection.inspections.api.dto.request.VerifyPermitRequest;
import com.smartdroneinspection.inspections.api.dto.response.MissionPlanResponse;
import com.smartdroneinspection.inspections.service.DroneMissionPlanService;
import com.smartdroneinspection.shared.api.ApiResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mission-plans")
public class DroneMissionPlanController {

  private final DroneMissionPlanService missionPlanService;

  public DroneMissionPlanController(DroneMissionPlanService missionPlanService) {
    this.missionPlanService = missionPlanService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR')")
  public ApiResponse<MissionPlanResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateMissionPlanRequest request) {
    return ApiResponse.success(missionPlanService.createDraft(subject(jwt), request));
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR', 'CLIENT', 'PLATFORM_ADMIN', 'PLATFORM_OPERATOR')")
  public ApiResponse<MissionPlanResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(missionPlanService.getMissionPlan(subject(jwt), id));
  }

  @GetMapping("/by-order/{serviceOrderId}")
  @PreAuthorize(
      "hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR', 'CLIENT', 'PLATFORM_ADMIN', 'PLATFORM_OPERATOR')")
  public ApiResponse<List<MissionPlanResponse>> listByOrder(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID serviceOrderId) {
    return ApiResponse.success(missionPlanService.listByOrder(subject(jwt), serviceOrderId));
  }

  @PutMapping("/{id}/sow")
  @PreAuthorize("hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR')")
  public ApiResponse<MissionPlanResponse> updateSow(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateMissionPlanRequest request) {
    return ApiResponse.success(missionPlanService.updateEquipmentAndSow(subject(jwt), id, request));
  }

  @PostMapping("/{id}/shot-items")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR')")
  public ApiResponse<MissionPlanResponse> addShotItem(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody AddShotItemRequest request) {
    return ApiResponse.success(missionPlanService.addShotItem(subject(jwt), id, request));
  }

  @PostMapping("/{id}/airspace-check")
  @PreAuthorize("hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR')")
  public ApiResponse<MissionPlanResponse> recordAirspacePreCheck(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody AirspaceCheckRequest request) {
    return ApiResponse.success(
        missionPlanService.recordAirspacePreCheck(subject(jwt), id, request));
  }

  @PostMapping("/{id}/verify-permit")
  @PreAuthorize("hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR')")
  public ApiResponse<MissionPlanResponse> verifyFlightPermit(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody VerifyPermitRequest request) {
    return ApiResponse.success(missionPlanService.verifyFlightPermit(subject(jwt), id, request));
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAnyRole('PROVIDER_MANAGER', 'INSPECTOR')")
  public ApiResponse<MissionPlanResponse> submit(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(missionPlanService.submit(subject(jwt), id));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<MissionPlanResponse> approve(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(missionPlanService.approve(subject(jwt), id));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
