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

  /**
   * @param narrative retained so a caller that still needs prose has it; the authored sections are
   *     the source of truth for what is displayed and stored
   */
  record SectionDraft(
      String executiveSummary,
      String purposeScopeCriteria,
      String methodEquipmentConditions,
      String conclusionsRecommendations,
      String overallCondition,
      String omissionDisclosure,
      String narrative) {}

  record DraftContext(
      List<ChecklistEntrySummary> checklistResponses,
      List<FindingSummary> findings,
      List<EvidenceSummary> evidence) {}

  record ChecklistEntrySummary(String prompt, String responseValue, String notes) {}

  record FindingSummary(
      String defectLabel,
      String severity,
      String component,
      String locationDescription,
      String technicalNotes,
      String recommendedAction) {}

  record EvidenceSummary(String fileName, String contentType) {}
}
