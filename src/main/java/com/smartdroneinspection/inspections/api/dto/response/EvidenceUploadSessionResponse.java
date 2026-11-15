package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
import com.smartdroneinspection.inspections.domain.enums.EvidenceUploadSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvidenceUploadSessionResponse(
    UUID sessionId,
    UUID inspectionId,
    UUID fieldSessionId,
    String fileName,
    String contentType,
    long declaredSizeBytes,
    int chunkSizeBytes,
    int totalParts,
    List<Integer> uploadedPartNumbers,
    EvidenceUploadSessionStatus status,
    UUID evidenceId,
    EvidenceProcessingStatus processingStatus,
    String errorMessage,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt) {}
