package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.response.FieldSessionFlightResponse;
import com.smartdroneinspection.inspections.service.InspectionDroneHubService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/flights")
public class InspectionFlightController {

  private final InspectionDroneHubService droneHub;

  public InspectionFlightController(InspectionDroneHubService droneHub) {
    this.droneHub = droneHub;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN', 'CUSTOMER')")
  public ApiResponse<List<FieldSessionFlightResponse>> listAll(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(
        droneHub.listFlightsForInspection(UUID.fromString(jwt.getSubject()), inspectionId));
  }
}
