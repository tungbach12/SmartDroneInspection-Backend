package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
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
    String sourceLocationDescription,
    BigDecimal sourceLatitude,
    BigDecimal sourceLongitude,
    String repairProofUrl,
    String repairEngineerNotes,
    UUID newEvidenceId,
    String newEvidenceFileKey,
    String resolutionStatus,
    String inspectorNotes,
    Instant createdAt,
    Instant updatedAt) {}
