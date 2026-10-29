package com.smartdroneinspection.users.api.dto.response;

import java.time.Instant;
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
    boolean visionFallbackEnabled,
    String pilotLicenseNumber,
    String pilotLicenseClass,
    Instant pilotLicenseExpiresAt,
    String pilotLicenseFileKey) {

  public UserResponse(
      UUID id,
      String email,
      String fullName,
      String phoneNumber,
      String avatarUrl,
      List<String> roles,
      String actorZone,
      UUID organizationId,
      boolean visionFallbackEnabled) {
    this(
        id,
        email,
        fullName,
        phoneNumber,
        avatarUrl,
        roles,
        actorZone,
        organizationId,
        visionFallbackEnabled,
        null,
        null,
        null,
        null);
  }

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

  public UserResponse(
      UUID id,
      String email,
      String fullName,
      List<String> roles,
      String actorZone,
      UUID organizationId) {
    this(id, email, fullName, roles, actorZone, organizationId, true);
  }
}
