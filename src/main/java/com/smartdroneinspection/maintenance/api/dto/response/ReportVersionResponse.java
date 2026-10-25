package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceReportVersion;
import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import java.time.Instant;
import java.util.UUID;

/** MF4-14/16: a completion report version bound to the facts that existed when it was written. */
public record ReportVersionResponse(
    UUID id,
    int versionNo,
    ReportVersionStatus status,
    UUID authorUserId,
    Instant authorVerifiedAt,
    String contentSnapshot,
    String renderedObjectKey,
    String renderedChecksumSha256) {

  public static ReportVersionResponse from(MaintenanceReportVersion report, UUID authorUserId) {
    return new ReportVersionResponse(
        report.getId(),
        report.getVersionNo(),
        report.getStatus(),
        authorUserId,
        report.getAuthorVerifiedAt(),
        report.getContentSnapshot(),
        report.getRenderedObjectKey(),
        report.getRenderedChecksumSha256());
  }
}
