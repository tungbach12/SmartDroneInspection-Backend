package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UploadFlightPermitRequest(
    @NotBlank @Size(max = 1000) String fileKey,
    @NotBlank @Size(max = 500) String fileName,
    @NotBlank @Size(max = 120) String permitNumber,
    @NotBlank @Size(max = 200) String issuingAuthority,
    @NotNull Instant validFrom,
    @NotNull Instant validTo,
    @Size(max = 120) String droneSerial,
    @Positive @Max(2000) Integer maxAltitudeM) {}
