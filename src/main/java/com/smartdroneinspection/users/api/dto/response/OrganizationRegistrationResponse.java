package com.smartdroneinspection.users.api.dto.response;

import java.util.UUID;

public record OrganizationRegistrationResponse(
    UUID organizationId,
    String organizationName,
    String organizationCode,
    UserResponse user,
    String registrationStatus,
    UUID airspaceReviewId,
    String airspaceClassification,
    String reviewMessage) {

  public OrganizationRegistrationResponse(
      UUID organizationId, String organizationName, String organizationCode, UserResponse user) {
    this(
        organizationId,
        organizationName,
        organizationCode,
        user,
        "ACTIVE",
        null,
        "CLEAR_ZONE",
        null);
  }
}
