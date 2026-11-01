package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationRegistrationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 32) String phoneNumber,
    @NotBlank @Size(max = 200) String organizationName,
    @NotBlank
        @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,63}",
            message =
                "Organization code must contain 3 to 64 letters, numbers, hyphens, or underscores.")
        String organizationCode,
    @NotBlank String password,
    @Valid FirstAssetRegistrationRequest firstAsset,
    String otpChallengeToken,
    String otpCode) {

  public OrganizationRegistrationRequest(
      String email,
      String fullName,
      String organizationName,
      String organizationCode,
      String password,
      FirstAssetRegistrationRequest firstAsset) {
    this(
        email,
        fullName,
        null,
        organizationName,
        organizationCode,
        password,
        firstAsset,
        null,
        null);
  }
}
