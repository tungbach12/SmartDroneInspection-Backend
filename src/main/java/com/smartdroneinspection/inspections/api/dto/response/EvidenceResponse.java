package com.smartdroneinspection.inspections.api.dto.response;

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
    Instant captureTime,
    BigDecimal latitude,
    BigDecimal longitude,
    UploadStatus uploadStatus,
    Instant createdAt,
    Instant retiredAt,
    String locationDescription,
    boolean deduplicated) {}
