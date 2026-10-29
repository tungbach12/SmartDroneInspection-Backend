package com.smartdroneinspection.users.api.dto.response;

import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import java.time.Instant;
import java.util.UUID;

public record ProspectiveCustomerResponse(
    UUID id,
    UUID userId,
    String organizationName,
    String organizationCode,
    String assetName,
    String assetCode,
    String airspaceClassification,
    String status,
    String reviewNote,
    UUID reviewedByUserId,
    Instant reviewedAt,
    Instant createdAt) {

  public static ProspectiveCustomerResponse from(ProspectiveCustomerRegistration registration) {
    return new ProspectiveCustomerResponse(
        registration.getId(),
        registration.getUserId(),
        registration.getOrganizationName(),
        registration.getOrganizationCode(),
        registration.getAssetName(),
        registration.getAssetCode(),
        registration.getAirspaceClassification(),
        registration.getStatus(),
        registration.getReviewNote(),
        registration.getReviewedByUserId(),
        registration.getReviewedAt(),
        registration.getCreatedAt());
  }
}
