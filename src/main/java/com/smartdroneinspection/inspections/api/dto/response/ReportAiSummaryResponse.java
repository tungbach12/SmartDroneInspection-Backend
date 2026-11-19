package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;

/** AI-generated executive digest for reviewer and client presentation. */
public record ReportAiSummaryResponse(
    String summary,
    List<String> keyHighlights,
    String recommendedActions,
    String overallRisk,
    List<KnowledgeReferenceSummary> knowledgeReferences) {

  /** Keeps the pre-knowledge constructors of callers and tests source-compatible. */
  public ReportAiSummaryResponse(
      String summary, List<String> keyHighlights, String recommendedActions, String overallRisk) {
    this(summary, keyHighlights, recommendedActions, overallRisk, List.of());
  }

  public ReportAiSummaryResponse {
    knowledgeReferences =
        knowledgeReferences == null ? List.of() : List.copyOf(knowledgeReferences);
  }
}
