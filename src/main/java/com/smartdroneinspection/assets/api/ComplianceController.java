package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CreateFlightPermitRequest;
import com.smartdroneinspection.assets.api.dto.request.ReviewFlightPermitRequest;
import com.smartdroneinspection.assets.api.dto.response.FlightPermitResponse;
import com.smartdroneinspection.assets.service.ComplianceService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization permit and compliance records.
 *
 * <p>The tenant is taken from the authenticated principal inside the service; no endpoint accepts
 * an organization or actor zone. Review here is an internal human decision by an administrator of
 * the customer's own organization and is not an external, government or authority verification of
 * the permit.
 */
@RestController
@RequestMapping("/api/v1/flight-permits")
public class ComplianceController {

  private final ComplianceService compliance;

  public ComplianceController(ComplianceService compliance) {
    this.compliance = compliance;
  }

  @PostMapping
  @PreAuthorize("hasRole('" + Roles.ORG_ADMIN + "')")
  public ResponseEntity<ApiResponse<FlightPermitResponse>> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateFlightPermitRequest body) {
    return ResponseEntity.status(201)
        .body(ApiResponse.success(compliance.create(subject(jwt), body)));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('" + Roles.ORG_ADMIN + "', '" + Roles.ADMIN + "')")
  public ApiResponse<List<FlightPermitResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(compliance.list(subject(jwt)));
  }

  /**
   * Records the internal review of a permit record: who read it, when, and why. This is an audit
   * trail inside the customer's own organization, not a determination by any authority.
   */
  @PutMapping("/{permitId}/review")
  @PreAuthorize("hasRole('" + Roles.ORG_ADMIN + "')")
  public ApiResponse<FlightPermitResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID permitId,
      @Valid @RequestBody ReviewFlightPermitRequest body) {
    return ApiResponse.success(compliance.review(subject(jwt), permitId, body));
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
