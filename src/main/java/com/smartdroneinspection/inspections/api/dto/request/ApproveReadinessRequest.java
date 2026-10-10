package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The MF2-07 approval a reviewer submits.
 *
 * <p>The reviewer is never named here. {@code @AuthenticationPrincipal Jwt} is the only accepted
 * reviewer identity, because a body that carried {@code reviewedByUserId} would let a caller record
 * a decision in someone else's name. Organization, subject ownership and credential scope are all
 * re-derived from the token inside the service.
 *
 * <p>An empty credential or document selection is not itself invalid, but it has to be explained:
 * {@code noInspectorCredentialReason} and {@code noDroneDocumentReason} exist so "nothing applies"
 * is a recorded judgement rather than an omission.
 */
public record ApproveReadinessRequest(
    @NotNull UUID reviewerCredentialId,
    List<UUID> inspectorCredentialIds,
    List<UUID> droneDocumentIds,
    boolean applicabilityComplete,
    @Size(max = 500) String applicabilityBasisReference,
    @Size(max = 500) String noInspectorCredentialReason,
    @Size(max = 500) String noDroneDocumentReason,
    @Size(max = 500) String humanVerificationBasis) {

  public ApproveReadinessRequest {
    inspectorCredentialIds =
        inspectorCredentialIds == null ? List.of() : List.copyOf(inspectorCredentialIds);
    droneDocumentIds = droneDocumentIds == null ? List.of() : List.copyOf(droneDocumentIds);
  }
}
