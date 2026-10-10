package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything MF2-07 asks a reviewer to look at, for one inspection.
 *
 * <p>Approval names a reviewer credential, the assigned Inspector's credentials and the assigned
 * Drone's documents. Those ids have to come from somewhere, and a client cannot invent them. This
 * response is that somewhere: the reviewer's own credential list is fetched separately by {@code
 * /workforce/credentials/me} because it describes the caller rather than the inspection.
 *
 * <p>The credentials and documents are summaries rather than rows. No document content and no
 * download link travels here; the reviewer reads the reference and the validity window, which is
 * what the decision is actually based on.
 */
public record ReadinessSourcesResponse(
    UUID inspectionId,
    UUID inspectorUserId,
    UUID droneId,
    List<CredentialSummaryResponse> inspectorCredentials,
    List<DocumentSummaryResponse> droneDocuments) {

  /** One credential the reviewer may rely on, as far as its stored record shows. */
  public record CredentialSummaryResponse(
      UUID id,
      String credentialType,
      String issuer,
      String credentialReference,
      Instant issuedAt,
      Instant expiresAt,
      String status,
      Instant verifiedAt) {}

  /** One Drone document the reviewer may rely on, as far as its stored record shows. */
  public record DocumentSummaryResponse(
      UUID id,
      String documentType,
      String issuer,
      String documentReference,
      Instant validFrom,
      Instant validUntil,
      String status,
      Instant reviewedAt) {}
}
