package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.DroneDocument;
import java.time.Instant;
import java.util.UUID;

public record DroneDocumentResponse(
    UUID id,
    UUID droneId,
    String documentType,
    String issuer,
    String documentReference,
    Instant validFrom,
    Instant validUntil,
    String status,
    String checksumSha256,
    UUID uploadedByUserId,
    UUID reviewedByUserId,
    Instant reviewedAt,
    Instant createdAt) {

  public static DroneDocumentResponse from(DroneDocument document) {
    return from(document, Instant.now());
  }

  /**
   * Maps the document as a reader should see it at {@code at}: a document whose validity has run
   * out is presented as {@code EXPIRED} even while its stored status still says otherwise, so a
   * stale permit cannot be read as a current one.
   */
  public static DroneDocumentResponse from(DroneDocument document, Instant at) {
    return new DroneDocumentResponse(
        document.getId(),
        document.getDrone().getId(),
        document.getDocumentType(),
        document.getIssuer(),
        document.getDocumentReference(),
        document.getValidFrom(),
        document.getValidUntil(),
        document.effectiveStatusAt(at).name(),
        document.getChecksumSha256(),
        document.getUploadedByUserId(),
        document.getReviewedByUserId(),
        document.getReviewedAt(),
        document.getCreatedAt());
  }
}
