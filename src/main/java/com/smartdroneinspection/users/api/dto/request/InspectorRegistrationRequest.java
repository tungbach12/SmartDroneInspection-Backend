package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record InspectorRegistrationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 32) String phoneNumber,
    @Size(min = 8, max = 200) String password,
    @Size(max = 32) String citizenId,
    @Size(max = 500) String address,
    @Size(max = 128) String pilotLicenseNumber,
    @NotBlank @Size(max = 64) String pilotLicenseClass,
    Instant pilotLicenseExpiresAt,
    String otpChallengeToken,
    String otpCode) {}
