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
    String approvedScopeHash,
    String changeSnapshotHash,
    String workLogSnapshotHash,
    String actualCostSnapshotHash,
    String contentSnapshot) {

  public static ReportVersionResponse from(MaintenanceReportVersion report) {
    return new ReportVersionResponse(
        report.getId(),
        report.getVersionNo(),
        report.getStatus(),
        report.getAuthorUserId(),
        report.getAuthorVerifiedAt(),
        report.getApprovedScopeHash(),
        report.getChangeSnapshotHash(),
        report.getWorkLogSnapshotHash(),
        report.getActualCostSnapshotHash(),
        report.getContentSnapshot());
  }
}
