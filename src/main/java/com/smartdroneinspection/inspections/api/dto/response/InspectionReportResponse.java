package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/** Inspection report contract with the current submission and reviewer decision. */
public record InspectionReportResponse(
    UUID id,
    UUID inspectionReportId,
    int versionNo,
    ReportStatus status,
    UUID authorUserId,
    Instant submittedAt,
    UUID reviewerUserId,
    Instant reviewedAt,
    String returnReason,
    Instant publishedAt,
    Instant createdAt,
    String summary,
    boolean clarificationRequested,
    InspectionReportContextResponse reportContext) {

  public InspectionReportResponse(
      UUID id,
      UUID inspectionReportId,
      int versionNo,
      ReportStatus status,
      UUID authorUserId,
      Instant submittedAt,
      UUID reviewerUserId,
      Instant reviewedAt,
      String returnReason,
      Instant publishedAt,
      Instant createdAt,
      String summary,
      InspectionReportContextResponse reportContext) {
    this(
        id,
        inspectionReportId,
        versionNo,
        status,
        authorUserId,
        submittedAt,
        reviewerUserId,
        reviewedAt,
        returnReason,
        publishedAt,
        createdAt,
        summary,
        false,
        reportContext);
  }
}
