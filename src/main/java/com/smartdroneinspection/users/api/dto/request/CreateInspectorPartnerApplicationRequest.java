package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateInspectorPartnerApplicationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 64) String phone,
    @NotBlank @Size(max = 128) String pilotLicenseNumber,
    @NotBlank @Size(max = 64) String pilotLicenseClass,
    @NotNull LocalDate pilotLicenseExpiresAt,
    @NotBlank @Size(max = 1000) String pilotLicenseFileKey) {}
