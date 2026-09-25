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
    List<FindingEntry> findings,
    String aiDraftNarrative) {

  public ReportSnapshot {
    checklist = checklist == null ? List.of() : List.copyOf(checklist);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  public ReportSnapshot withAiDraftNarrative(String narrative) {
    return new ReportSnapshot(
        inspectionId,
        serviceOrderId,
        assetId,
        checklistTemplateId,
        checklistName,
        generatedAt,
        checklist,
        evidence,
        findings,
        narrative);
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
