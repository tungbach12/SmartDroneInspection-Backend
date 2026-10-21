package com.smartdroneinspection.users.api.dto.response;

import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    String phoneNumber,
    String avatarUrl,
    List<String> roles,
    String actorZone,
    UUID organizationId,
    boolean visionFallbackEnabled) {

  public UserResponse(
      UUID id,
      String email,
      String fullName,
      List<String> roles,
      String actorZone,
      UUID organizationId,
      boolean visionFallbackEnabled) {
    this(id, email, fullName, null, null, roles, actorZone, organizationId, visionFallbackEnabled);
  }
}
