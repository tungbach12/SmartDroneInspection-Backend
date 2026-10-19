package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.ReportSections;

/** Authored section values. Null means the author has not supplied that field. */
public record ReportSectionsResponse(
    String executiveSummary,
    String purposeScopeCriteria,
    String inspectorCoverageAssessment,
    String methodEquipmentConditions,
    String conclusionsRecommendations,
    ReportSections.OverallCondition overallCondition,
    String omissionDisclosure) {

  public static ReportSectionsResponse from(ReportSections sections) {
    return new ReportSectionsResponse(
        sections.executiveSummary(),
        sections.purposeScopeCriteria(),
        sections.inspectorCoverageAssessment(),
        sections.methodEquipmentConditions(),
        sections.conclusionsRecommendations(),
        sections.overallCondition(),
        sections.omissionDisclosure());
  }
}
