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
    boolean visionFallbackEnabled,
    String pilotLicenseNumber,
    String pilotLicenseClass,
    Instant pilotLicenseExpiresAt,
    String pilotLicenseFileKey,
    String payoutBankName,
    String payoutBankAccountNumber,
    String payoutBankAccountHolder,
    String payoutBankBranch) {

  public UserResponse(
      UUID id,
      String email,
      String fullName,
      String phoneNumber,
      String avatarUrl,
      List<String> roles,
      boolean visionFallbackEnabled,
      String pilotLicenseNumber,
      String pilotLicenseClass,
      Instant pilotLicenseExpiresAt,
      String pilotLicenseFileKey) {
    this(
        id,
        email,
        fullName,
        phoneNumber,
        avatarUrl,
        roles,
        visionFallbackEnabled,
        pilotLicenseNumber,
        pilotLicenseClass,
        pilotLicenseExpiresAt,
        pilotLicenseFileKey,
        null,
        null,
        null,
        null);
  }

  public UserResponse(
      UUID id,
      String email,
      String fullName,
      String phoneNumber,
      String avatarUrl,
      List<String> roles,
      boolean visionFallbackEnabled) {
    this(
        id,
        email,
        fullName,
        phoneNumber,
        avatarUrl,
        roles,
        visionFallbackEnabled,
        null,
        null,
        null,
        null);
  }

  public UserResponse(
      UUID id, String email, String fullName, List<String> roles, boolean visionFallbackEnabled) {
    this(id, email, fullName, null, null, roles, visionFallbackEnabled);
  }

  public UserResponse(UUID id, String email, String fullName, List<String> roles) {
    this(id, email, fullName, roles, true);
  }
}
