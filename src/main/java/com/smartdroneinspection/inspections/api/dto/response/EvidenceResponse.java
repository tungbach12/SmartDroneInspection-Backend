package com.smartdroneinspection.inspections.api.dto.response;

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
    String objectKey,
    Instant captureTime,
    BigDecimal latitude,
    BigDecimal longitude,
    String locationDescription,
    boolean isDeleted,
    Instant createdAt) {}
