package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.util.List;

/** Outbound port for generating draft report narratives from sanitized inspection data. */
public interface ReportDraftPort {

  String generateDraft(DraftContext context) throws IOException;

  record DraftContext(
      List<ChecklistEntrySummary> checklistResponses,
      List<FindingSummary> findings,
      List<EvidenceSummary> evidence) {}

  record ChecklistEntrySummary(String prompt, String responseValue, String notes) {}

  record FindingSummary(String defectLabel, String severity, String technicalNotes) {}

  record EvidenceSummary(String fileName, String contentType) {}
}
