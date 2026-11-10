package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    List<String> ragReferenceStandards,
    String ragVerificationGuidance,
    Instant createdAt,
    Instant updatedAt) {

  public ReinspectionVerificationResponse(
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
      Instant updatedAt) {
    this(
        id,
        reinspectionId,
        sourceFindingId,
        sourceFindingCode,
        sourceDefectLabel,
        sourceSeverity,
        sourceTechnicalNotes,
        sourceEvidenceId,
        sourceEvidenceFileKey,
        sourceLocationDescription,
        sourceLatitude,
        sourceLongitude,
        repairProofUrl,
        repairEngineerNotes,
        newEvidenceId,
        newEvidenceFileKey,
        resolutionStatus,
        inspectorNotes,
        List.of(),
        null,
        createdAt,
        updatedAt);
  }
}
