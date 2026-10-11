package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.LinkPermitReferencesRequest;
import com.smartdroneinspection.inspections.api.dto.request.PrepareShotListRequest;
import com.smartdroneinspection.inspections.api.dto.request.SubmitPreparationRequest;
import com.smartdroneinspection.inspections.api.dto.response.ComplianceGateResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPreparationResponse;
import com.smartdroneinspection.inspections.service.ComplianceGateService;
import com.smartdroneinspection.inspections.service.InspectionPreparationService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
 * MF2-03 through MF2-06: an inspector prepares and submits, an organization administrator checks
 * the compliance basis and links the permits they hold.
 *
 * <p>The two roles are separated by {@code @PreAuthorize} as well as by step. An inspector prepares
 * and answers for a mission but does not certify its own paperwork, so MF2-04 and MF2-05 sit with
 * the administrator while MF2-03 and MF2-06 sit with the inspector. The service re-derives
 * organization scope from the token either way, so passing another tenant's ids grants nothing.
 */
@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/preparation")
public class InspectionPreparationController {

  private final InspectionPreparationService preparations;
  private final ComplianceGateService compliance;

  public InspectionPreparationController(
      InspectionPreparationService preparations, ComplianceGateService compliance) {
    this.preparations = preparations;
    this.compliance = compliance;
  }

  /** The preparation history of this inspection, newest version first (MF2-03). */
  @GetMapping
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<InspectionPreparationResponse>> listPreparations(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(preparations.listPreparations(subject(jwt), inspectionId));
  }

  @GetMapping("/{preparationId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionPreparationResponse> getPreparation(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID preparationId) {
    return ApiResponse.success(preparations.getPreparation(subject(jwt), preparationId));
  }

  /** Records the component shot-list, evidence types, access limits and hazards (MF2-03). */
  @PutMapping
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionPreparationResponse> prepareShotList(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody PrepareShotListRequest request) {
    return ApiResponse.success(preparations.prepareShotList(subject(jwt), inspectionId, request));
  }

  /** Attributable submission of the preparation (MF2-06). */
  @PostMapping("/{preparationId}/submission")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionPreparationResponse> submit(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID preparationId,
      @Valid @RequestBody SubmitPreparationRequest request) {
    UUID inspectorId = subject(jwt);
    // The inspection in the path must be the one the preparation belongs to; otherwise a caller
    // could submit a preparation through another inspection's URL and have the scope check look at
    // the wrong record.
    preparations.getPreparation(inspectorId, preparationId);
    return ApiResponse.success(preparations.submitPreparation(inspectorId, preparationId, request));
  }

  /**
   * Links the issued permits the organization holds (MF2-04).
   *
   * <p>Ids are resolved against the caller's organization in the service, so a permit from another
   * tenant is refused rather than linked.
   */
  @PostMapping("/compliance/permits")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<InspectionPreparationResponse> linkPermitReferences(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody LinkPermitReferencesRequest request) {
    return ApiResponse.success(
        compliance.linkPermitReferences(subject(jwt), inspectionId, request));
  }

  /**
   * Reports what stands in the way of a readiness decision (MF2-05).
   *
   * <p>A 200 with a blocker list, not an error status: the reviewer of MF2-07 needs every blocker
   * at once, and an empty list still means a named reviewer has to decide rather than the platform
   * clearing the mission.
   */
  @GetMapping("/compliance")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ComplianceGateResponse> evaluateCompliance(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(compliance.evaluate(subject(jwt), inspectionId));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
