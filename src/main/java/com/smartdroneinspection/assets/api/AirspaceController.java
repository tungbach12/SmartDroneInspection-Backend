package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.EvaluateAirspaceRequest;
import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.assets.service.AirspaceAdvisoryService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airspace")
@PreAuthorize("hasAnyRole('CUSTOMER', 'INSPECTOR', 'MAINTENANCE_ENGINEER', 'MANAGER', 'ADMIN')")
public class AirspaceController {

  private final AirspaceAdvisoryService airspaceService;

  public AirspaceController(AirspaceAdvisoryService airspaceService) {
    this.airspaceService = airspaceService;
  }

  @GetMapping("/zones")
  public ApiResponse<List<AirspaceZoneResponse>> listZones() {
    return ApiResponse.success(airspaceService.listZones());
  }

  @GetMapping("/evaluate")
  public ApiResponse<AirspaceEvaluationResponse> evaluateGet(
      @RequestParam BigDecimal latitude, @RequestParam BigDecimal longitude) {
    return ApiResponse.success(airspaceService.evaluate(latitude, longitude));
  }

  @PostMapping("/evaluate")
  public ApiResponse<AirspaceEvaluationResponse> evaluate(
      @Valid @RequestBody EvaluateAirspaceRequest request) {
    return ApiResponse.success(airspaceService.evaluate(request.latitude(), request.longitude()));
  }
}
