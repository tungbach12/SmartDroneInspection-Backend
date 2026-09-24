package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

public record EvidenceUploadMetadataRequest(
    @NotNull EvidenceSource source,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant captureTime,
    @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
    @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
    @Size(max = 200) String externalReference) {}
