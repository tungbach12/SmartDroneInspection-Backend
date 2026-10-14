package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.FlightPermit;
import java.time.Instant;
import java.util.UUID;

/**
 * A permit record exactly as the organization recorded it.
 *
 * <p>The field set is deliberately limited to what was transcribed and who reviewed it internally.
 * There is no verification, validation, authenticity or clearance property, because this slice
 * performs no such check: no registry, authority or government service is contacted anywhere in
 * this codebase.
 */
public record FlightPermitResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    String areaReference,
    String permitType,
    String issuingAuthority,
    String permitReference,
    String geographicScope,
    Instant validFrom,
    Instant validUntil,
    String conditions,
    String status,
    UUID sourceEvidenceId,
    UUID reviewedByUserId,
    Instant reviewedAt,
    String reviewReason,
    Instant createdAt) {

  public static FlightPermitResponse from(FlightPermit permit) {
    return new FlightPermitResponse(
        permit.getId(),
        permit.getOrganizationId(),
        permit.getAssetId(),
        permit.getAreaReference(),
        permit.getPermitType(),
        permit.getIssuingAuthority(),
        permit.getPermitReference(),
        permit.getGeographicScope(),
        permit.getValidFrom(),
        permit.getValidUntil(),
        permit.getConditions(),
        permit.getRecordedStatus().name(),
        permit.getSourceEvidenceId(),
        permit.getReviewedByUserId(),
        permit.getReviewedAt(),
        permit.getReviewReason(),
        permit.getCreatedAt());
  }
}
