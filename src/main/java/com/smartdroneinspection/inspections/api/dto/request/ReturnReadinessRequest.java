package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The MF2-07 return a reviewer submits.
 *
 * <p>The two observed id lists are what the reviewer actually looked at, not an exhaustive catalog
 * of what exists. A return is allowed precisely because the readiness evidence may be incomplete,
 * so the decision records the observed set and marks it incomplete rather than implying the
 * reviewer cleared every source. {@code reason} is required: a rejection nobody can read is not a
 * usable instruction to the inspector.
 */
public record ReturnReadinessRequest(
    @NotNull UUID reviewerCredentialId,
    List<UUID> inspectorCredentialIdsObserved,
    List<UUID> droneDocumentIdsObserved,
    @NotNull @Size(max = 2000) String reason) {

  public ReturnReadinessRequest {
    inspectorCredentialIdsObserved =
        inspectorCredentialIdsObserved == null
            ? List.of()
            : List.copyOf(inspectorCredentialIdsObserved);
    droneDocumentIdsObserved =
        droneDocumentIdsObserved == null ? List.of() : List.copyOf(droneDocumentIdsObserved);
  }
}
