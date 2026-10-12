package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.ReportSections;

/**
 * The authored Report 3 §3.7.2 sections of a version, as the authoring form reads and writes them.
 *
 * <p>A null field is an absent section and renders as "not supplied". It is not an empty value the
 * author chose, and the client must not present it as one.
 */
public record ReportSectionsResponse(
    String executiveSummary,
    String purposeScopeCriteria,
    String methodEquipmentConditions,
    String conclusionsRecommendations,
    ReportSections.OverallCondition overallCondition,
    String omissionDisclosure) {

  public static ReportSectionsResponse from(ReportSections sections) {
    return new ReportSectionsResponse(
        sections.executiveSummary(),
        sections.purposeScopeCriteria(),
        sections.methodEquipmentConditions(),
        sections.conclusionsRecommendations(),
        sections.overallCondition(),
        sections.omissionDisclosure());
  }
}
