package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReinspectionVerificationResponse(
    UUID id,
    UUID reinspectionId,
    UUID sourceFindingId,
    String sourceFindingCode,
    String sourceDefectLabel,
    String sourceSeverity,
    String sourceTechnicalNotes,
    UUID sourceEvidenceId,
    String sourceEvidenceFileKey,
    UUID newEvidenceId,
    String newEvidenceFileKey,
    String resolutionStatus,
    String inspectorNotes,
    Instant createdAt,
    Instant updatedAt) {}
