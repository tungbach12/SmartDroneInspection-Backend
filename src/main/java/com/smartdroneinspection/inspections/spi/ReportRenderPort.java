package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Outbound port for rendering a report version to its delivered document.
 *
 * <p>The document is built from the stored section values and the records each section describes,
 * never from an authoring screen: MF3-11 delivers a report, not a capture of the UI that produced
 * it.
 */
public interface ReportRenderPort {

  byte[] render(RenderContext context) throws IOException;

  /**
   * @param documentRef the reference printed on every page, e.g. {@code SDI-RPT-…-V3}
   * @param sections the authored Report 3 §3.7.2 section values, absent values rendered as not
   *     supplied
   * @param findings the confirmed findings linked to this version
   * @param evidence the evidence linked to this version, with the bytes of the originals for
   *     embedding
   */
  record RenderContext(
      String documentRef,
      int versionNo,
      String status,
      String objective,
      String assetLabel,
      String organizationLabel,
      String authorName,
      String reviewerName,
      String findingsConfirmedAt,
      ReportContext reportContext,
      String submittedAt,
      String reviewedAt,
      String publishedAt,
      Sections sections,
      List<FindingView> findings,
      List<EvidenceView> evidence,
      Locale locale) {
    public RenderContext(
        String documentRef,
        int versionNo,
        String status,
        String objective,
        String assetLabel,
        String organizationLabel,
        String authorName,
        String reviewerName,
        String findingsConfirmedAt,
        ReportContext reportContext,
        String authorVerifiedAt,
        String reviewedAt,
        String publishedAt,
        String evidenceSnapshotHash,
        Sections sections,
        List<FindingView> findings,
        List<EvidenceView> evidence) {
      this(
          documentRef,
          versionNo,
          status,
          objective,
          assetLabel,
          organizationLabel,
          authorName,
          reviewerName,
          findingsConfirmedAt,
          reportContext,
          authorVerifiedAt,
          reviewedAt,
          publishedAt,
          evidenceSnapshotHash,
          sections,
          findings,
          evidence,
          Locale.ENGLISH);
    }
  }

  record ReportContext(
      String organizationName,
      String assetCode,
      String assetName,
      String assetLocation,
      String assetCategory,
      String assignedDroneSerialNumber,
      String assignedDroneManufacturer,
      String assignedDroneModel,
      String preparationStatus,
      String shotList,
      String evidenceTypes,
      String accessConstraints,
      String safetyObservations,
      String permitDocumentReferences,
      String readinessDecision,
      String readinessDecisionDate,
      String readinessReason,
      List<String> checklistResponseSummaries,
      List<String> fieldSessionSummaries,
      String reviewerQualificationDisclosure) {
    public ReportContext {
      checklistResponseSummaries =
          checklistResponseSummaries == null ? List.of() : List.copyOf(checklistResponseSummaries);
      fieldSessionSummaries =
          fieldSessionSummaries == null ? List.of() : List.copyOf(fieldSessionSummaries);
    }
  }

  record Sections(
      String executiveSummary,
      String purposeScopeCriteria,
      String inspectorCoverageAssessment,
      String methodEquipmentConditions,
      String conclusionsRecommendations,
      String overallCondition,
      String omissionDisclosure) {}

  record FindingView(
      String id,
      String evidenceId,
      String evidenceFileName,
      String defectLabel,
      String severity,
      String technicalNotes,
      String recommendedAction,
      String decision,
      String decidedAt,
      boolean inspectorConfirmed) {}

  record EvidenceView(
      String id,
      String fileName,
      String contentType,
      String captureTime,
      String componentLocation,
      String fieldSessionId,
      byte[] content) {}
}
