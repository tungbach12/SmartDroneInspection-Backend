package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;

/** AI-generated executive digest for reviewer and client presentation. */
public record ReportAiSummaryResponse(
    String summary, List<String> keyHighlights, String recommendedActions, String overallRisk) {}
