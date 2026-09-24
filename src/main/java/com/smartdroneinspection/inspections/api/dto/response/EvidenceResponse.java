package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
    UUID evidenceId,
    String fileName,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    EvidenceSource source,
    Instant captureTime,
    BigDecimal latitude,
    BigDecimal longitude,
    String externalReference,
    UploadStatus uploadStatus,
    Instant createdAt) {}
