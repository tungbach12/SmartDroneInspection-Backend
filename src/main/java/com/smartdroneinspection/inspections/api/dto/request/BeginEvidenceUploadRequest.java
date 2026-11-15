package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BeginEvidenceUploadRequest(
    @NotBlank @Size(max = 500) String fileName,
    @NotBlank @Size(max = 160) String contentType,
    @Positive long totalSizeBytes,
    UUID fieldSessionId,
    Instant captureTime,
    @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
    @Size(max = 1000) String locationDescription) {}
