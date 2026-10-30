package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.api.dto.request.UpdateInspectionFinanceSettingsRequest;
import com.smartdroneinspection.contracts.api.dto.response.InspectionFinanceSettingsResponse;
import com.smartdroneinspection.contracts.service.InspectionFinanceSettingsService;
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
@RequestMapping("/api/v1/contracts/admin/finance-settings")
public class InspectionFinanceSettingsController {

  private final InspectionFinanceSettingsService financeSettings;

  public InspectionFinanceSettingsController(InspectionFinanceSettingsService financeSettings) {
    this.financeSettings = financeSettings;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<InspectionFinanceSettingsResponse> getSettings(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(financeSettings.getSettings(actorId(jwt)));
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<InspectionFinanceSettingsResponse> updateSettings(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateInspectionFinanceSettingsRequest request) {
    return ApiResponse.success(
        financeSettings.updateSettings(
            actorId(jwt), request.commissionRate(), request.vatRate(), request.tncnRate()));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
