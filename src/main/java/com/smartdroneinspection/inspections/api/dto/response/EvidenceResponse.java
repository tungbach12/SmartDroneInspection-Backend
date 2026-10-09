package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
    UUID id,
    UUID inspectionId,
    UUID fieldSessionId,
    String fileName,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    Instant captureTime,
    EvidenceSource source,
    BigDecimal latitude,
    BigDecimal longitude,
    String externalReference,
    UploadStatus uploadStatus,
    Instant createdAt) {}
