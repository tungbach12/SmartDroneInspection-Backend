package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderRegistrationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Size(max = 200) String providerName,
    @NotBlank @Size(max = 250) String legalName,
    @NotBlank @Size(max = 32) String taxCode,
    @NotBlank @Size(max = 64) String businessLicenseNo,
    @NotBlank String password) {}
