package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record InspectorApplicationSubmissionRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 32) String phoneNumber,
    @NotBlank @Size(max = 32) String citizenId,
    @NotBlank @Size(max = 500) String address,
    @NotBlank @Size(max = 128) String pilotLicenseNumber,
    @NotBlank @Size(max = 64) String pilotLicenseClass,
    Instant pilotLicenseExpiresAt,
    @NotBlank @Size(max = 1000) String licenseScanFileKey,
    @NotBlank @Size(min = 8, max = 200) String password,
    @NotBlank String otpChallengeToken,
    @NotBlank @Size(min = 6, max = 6) String otpCode) {}
