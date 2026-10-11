package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.util.List;

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
      String authorVerifiedAt,
      String reviewedAt,
      String publishedAt,
      String evidenceSnapshotHash,
      Sections sections,
      List<FindingView> findings,
      List<EvidenceView> evidence) {}

  record Sections(
      String executiveSummary,
      String purposeScopeCriteria,
      String methodEquipmentConditions,
      String conclusionsRecommendations,
      String overallCondition,
      String omissionDisclosure) {}

  record FindingView(
      String id,
      String component,
      String defectLabel,
      String severity,
      String locationDescription,
      String observedCondition,
      String description,
      String technicalNotes,
      String recommendedAction,
      String decision,
      String decidedBy,
      String decidedAt,
      String measurement,
      String repairRequired,
      List<String> evidenceFileNames) {}

  record EvidenceView(
      String id,
      String fileName,
      String contentType,
      String checksumSha256,
      String captureTime,
      String componentLocation,
      byte[] content) {}
}
