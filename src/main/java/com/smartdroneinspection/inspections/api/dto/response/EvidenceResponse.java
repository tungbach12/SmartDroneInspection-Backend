package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
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
    Long originalSizeBytes,
    String objectKey,
    Instant captureTime,
    BigDecimal latitude,
    BigDecimal longitude,
    String locationDescription,
    boolean isDeleted,
    Instant createdAt,
    EvidenceProcessingStatus processingStatus,
    String processingError,
    boolean hasThumbnail,
    Integer mediaWidth,
    Integer mediaHeight,
    Double durationSeconds,
    Instant processedAt,
    UUID sourceVideoEvidenceId,
    Double sourceTimestampSeconds) {}
