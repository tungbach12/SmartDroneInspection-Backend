package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportSnapshot(
    UUID inspectionId,
    UUID serviceOrderId,
    UUID assetId,
    UUID checklistTemplateId,
    String checklistName,
    Instant generatedAt,
    List<ChecklistEntry> checklist,
    List<EvidenceEntry> evidence,
    List<FindingEntry> findings) {

  public ReportSnapshot {
    checklist = List.copyOf(checklist);
    evidence = List.copyOf(evidence);
    findings = List.copyOf(findings);
  }

  public record ChecklistEntry(
      UUID itemId,
      String itemCode,
      String prompt,
      boolean required,
      String responseValue,
      String notes,
      Instant completedAt) {}

  public record EvidenceEntry(
      UUID id,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String source,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude) {}

  public record FindingEntry(
      UUID id,
      String findingCode,
      UUID evidenceId,
      VerifiedFindingSource source,
      String defectLabel,
      FindingSeverity severity,
      String locationDescription,
      String technicalNotes,
      String recommendedAction,
      String boundingBox) {}
}
