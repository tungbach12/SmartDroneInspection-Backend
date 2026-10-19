package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.util.List;

/** Outbound port for generating draft report narratives from sanitized inspection data. */
public interface ReportDraftPort {

  /** Identifies the model that produced a draft, so a report can disclose its provenance. */
  String modelName();

  /**
   * Proposes values for the report's authored sections.
   *
   * <p>Per-section proposals rather than one narrative blob, because MF3-08 requires the author to
   * read and edit the entire draft and remove unsupported causes and measurements. A single block
   * of prose hides an unsupported claim inside a paragraph; a field does not.
   *
   * <p>Implementations must not write to the reviewed finding records and must not invent
   * telemetry, calibration, measured dimensions, dates, costs or signatures. Absent data stays
   * absent.
   */
  SectionDraft draftSections(DraftContext context) throws IOException;

  record SectionDraft(
      String executiveSummary,
      String purposeScopeCriteria,
      String methodEquipmentConditions,
      String conclusionsRecommendations,
      String overallCondition,
      String omissionDisclosure) {}

  record DraftContext(
      InspectionSummary inspection,
      List<ChecklistEntrySummary> checklistResponses,
      List<FindingSummary> findings,
      List<EvidenceSummary> evidence,
      List<CandidateSummary> candidates,
      List<FieldSessionSummary> fieldSessions,
      EvidenceQualitySummary evidenceQuality) {}

  record EvidenceQualitySummary(
      String decision, String shotListComparison, String limitationReason) {}

  record CandidateSummary(
      String candidateId,
      String evidenceId,
      String provider,
      String modelName,
      String modelVersion,
      String label,
      String confidence,
      String boundingBoxJson,
      String status) {}

  record FieldSessionSummary(
      String sessionId,
      String status,
      String startedAt,
      String endedAt,
      String preFlightChecklistNote,
      String postponementReason,
      String abortReason,
      String limitations,
      String assignedDroneId) {}

  record InspectionSummary(
      String objective,
      String scope,
      String componentScope,
      String acceptanceCriteria,
      String plannedStartAt,
      String plannedEndAt,
      String assignedDroneId) {}

  record ChecklistEntrySummary(String prompt, String responseValue, String notes) {}

  record FindingSummary(
      String defectLabel,
      String severity,
      String component,
      String locationDescription,
      String technicalNotes,
      String recommendedAction,
      String description,
      String observedCondition,
      String priority,
      String measurement) {}

  record EvidenceSummary(
      String fileName,
      String contentType,
      String captureTime,
      String latitude,
      String longitude,
      String source,
      String fieldSessionId,
      String locationDescription,
      String externalReference) {}
}
