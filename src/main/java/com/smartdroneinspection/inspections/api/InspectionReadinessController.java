package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.ApproveReadinessRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReturnReadinessRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReadinessDecisionResponse;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.service.InspectionReadinessService;
import com.smartdroneinspection.inspections.service.ReadinessReturnCommand;
import com.smartdroneinspection.inspections.service.ReadinessReviewCommand;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MF2-07: the qualified reviewer's approval or return of a submitted preparation.
 *
 * <p>Separate from {@link InspectionPreparationController} because the two actors are different: an
 * inspector prepares and submits, an organization administrator decides. The route check is
 * navigation policy only — the service independently re-derives the organization from the token, so
 * an ORG_ADMIN from another tenant receives nothing regardless of which ids the body carries.
 */
@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/readiness")
public class InspectionReadinessController {

  private final InspectionReadinessService readiness;

  public InspectionReadinessController(InspectionReadinessService readiness) {
    this.readiness = readiness;
  }

  @PostMapping("/{preparationId}/approval")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ReadinessDecisionResponse> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID preparationId,
      @Valid @RequestBody ApproveReadinessRequest request) {
    return ApiResponse.success(
        response(
            readiness.approve(
                subject(jwt),
                inspectionId,
                preparationId,
                new ReadinessReviewCommand(
                    request.reviewerCredentialId(),
                    request.inspectorCredentialIds(),
                    request.droneDocumentIds(),
                    request.applicabilityComplete(),
                    request.applicabilityBasisReference(),
                    request.noInspectorCredentialReason(),
                    request.noDroneDocumentReason(),
                    request.humanVerificationBasis()))));
  }

  @PostMapping("/{preparationId}/return")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ReadinessDecisionResponse> returnPreparation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID preparationId,
      @Valid @RequestBody ReturnReadinessRequest request) {
    return ApiResponse.success(
        response(
            readiness.returnPreparation(
                subject(jwt),
                inspectionId,
                preparationId,
                new ReadinessReturnCommand(
                    request.reviewerCredentialId(),
                    request.inspectorCredentialIdsObserved(),
                    request.droneDocumentIdsObserved(),
                    request.reason()))));
  }

  private ReadinessDecisionResponse response(InspectionReadinessDecision decision) {
    return new ReadinessDecisionResponse(
        decision.getId(),
        decision.getInspectionId(),
        decision.getPreparationId(),
        decision.getPreparationVersion(),
        decision.getDecision(),
        decision.getReviewedByUserId(),
        decision.getReason(),
        decision.getSourceHash(),
        decision.getDecidedAt());
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
