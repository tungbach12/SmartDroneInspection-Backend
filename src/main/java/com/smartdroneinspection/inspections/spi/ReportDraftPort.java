package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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

  /** Summarizes an inspection report for executive review by managers or viewing by customers. */
  default AiSummary summarizeReport(DraftContext context) throws IOException {
    return null;
  }

  record AiSummary(
      String summary, List<String> keyHighlights, String recommendedActions, String overallRisk) {}

  record DefectVerificationSummary(
      String defectLabel, String severity, String resolutionStatus, String inspectorNotes) {}

  record SectionDraft(
      String executiveSummary,
      String purposeScopeCriteria,
      String methodEquipmentConditions,
      String conclusionsRecommendations,
      String overallCondition,
      String omissionDisclosure) {}

  record DraftContext(
      InspectionSummary inspection,
      List<FindingSummary> findings,
      List<EvidenceSummary> evidence,
      List<CandidateSummary> candidates,
      List<FieldSessionSummary> fieldSessions,
      Locale locale,
      List<DefectVerificationSummary> defectVerifications) {
    public DraftContext(
        InspectionSummary inspection,
        List<FindingSummary> findings,
        List<EvidenceSummary> evidence,
        List<CandidateSummary> candidates,
        List<FieldSessionSummary> fieldSessions,
        Locale locale) {
      this(inspection, findings, evidence, candidates, fieldSessions, locale, List.of());
    }

    public DraftContext(
        InspectionSummary inspection,
        List<FindingSummary> findings,
        List<EvidenceSummary> evidence,
        List<CandidateSummary> candidates,
        List<FieldSessionSummary> fieldSessions) {
      this(inspection, findings, evidence, candidates, fieldSessions, Locale.ENGLISH, List.of());
    }
  }

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
      String assignedDroneId) {}

  record InspectionSummary(
      List<String> inspectionItems,
      String flightPermitFileName,
      String plannedStartAt,
      String plannedEndAt,
      String assignedDroneId,
      String inspectionType) {

    public InspectionSummary(
        List<String> inspectionItems,
        String flightPermitFileName,
        String plannedStartAt,
        String plannedEndAt,
        String assignedDroneId) {
      this(
          inspectionItems,
          flightPermitFileName,
          plannedStartAt,
          plannedEndAt,
          assignedDroneId,
          "INITIAL");
    }

    public InspectionSummary {
      inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
    }
  }

  record FindingSummary(
      String defectLabel, String severity, String technicalNotes, String recommendedAction) {}

  record EvidenceSummary(
      String fileName,
      String contentType,
      String captureTime,
      String latitude,
      String longitude,
      String fieldSessionId,
      String locationDescription) {}
}
