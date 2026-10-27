package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.assets.service.AirspaceAdvisoryService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airspace")
@PreAuthorize(
    "hasAnyRole('ORG_MANAGER', 'INSPECTOR', 'MAINTENANCE_ENGINEER', 'PLATFORM_MANAGER', 'ADMIN')")
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
  public ApiResponse<AirspaceEvaluationResponse> evaluate(
      @RequestParam BigDecimal latitude, @RequestParam BigDecimal longitude) {
    return ApiResponse.success(airspaceService.evaluate(latitude, longitude));
  }
}
