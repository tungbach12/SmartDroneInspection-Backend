package com.smartdroneinspection.users.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdatePilotLicenseRequest(
    @JsonAlias({"pilotLicenseNumber", "licenseNumber"}) @NotBlank @Size(max = 100)
        String licenseNumber,
    @JsonAlias({"pilotLicenseClass", "licenseClass"}) @Size(max = 16) String licenseClass,
    @JsonAlias({"pilotLicenseExpiresAt", "expiresAt"}) Instant expiresAt,
    @JsonAlias({"pilotLicenseFileKey", "fileKey"}) @Size(max = 1000) String fileKey) {}
