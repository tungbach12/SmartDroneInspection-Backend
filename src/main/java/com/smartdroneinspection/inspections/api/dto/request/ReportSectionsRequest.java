package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.ReportSections;
import jakarta.validation.constraints.Size;

/**
 * MF3-08 section authoring. The author saves the Report 3 §3.7.2 sections they are accountable for.
 *
 * <p>The request carries the whole authored set rather than a single section, so a partial write
 * cannot leave a version half-updated: either the sections the author submitted are stored, or
 * nothing is. Derived sections — the Evidence register, Confirmed findings, Approval block and
 * Appendices — are not part of this request because they are read from the records they describe
 * rather than written here.
 */
public record ReportSectionsRequest(
    @Size(max = ReportSections.MAX_SECTION_CHARS) String executiveSummary,
    @Size(max = ReportSections.MAX_SECTION_CHARS) String purposeScopeCriteria,
    @Size(max = ReportSections.MAX_SECTION_CHARS) String methodEquipmentConditions,
    @Size(max = ReportSections.MAX_SECTION_CHARS) String conclusionsRecommendations,
    ReportSections.OverallCondition overallCondition,
    @Size(max = ReportSections.MAX_DISCLOSURE_CHARS) String omissionDisclosure) {}
