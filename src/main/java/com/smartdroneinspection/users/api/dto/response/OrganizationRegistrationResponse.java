package com.smartdroneinspection.users.api.dto.response;

import java.util.UUID;

public record OrganizationRegistrationResponse(
    String organizationName,
    String organizationCode,
    UserResponse user,
    String registrationStatus,
    UUID airspaceReviewId,
    String airspaceClassification,
    String reviewMessage) {

  public OrganizationRegistrationResponse(
      String organizationName, String organizationCode, UserResponse user) {
    this(organizationName, organizationCode, user, "ACTIVE", null, "CLEAR_ZONE", null);
  }
}
