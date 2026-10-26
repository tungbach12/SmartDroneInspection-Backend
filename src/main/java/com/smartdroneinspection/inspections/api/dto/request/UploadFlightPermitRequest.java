package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UploadFlightPermitRequest(
    @NotBlank @Size(max = 1000) String fileKey, @NotBlank @Size(max = 500) String fileName) {}
