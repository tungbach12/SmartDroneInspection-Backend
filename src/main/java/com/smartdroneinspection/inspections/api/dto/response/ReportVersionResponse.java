package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.PeerReviewDecision;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

public record ReportVersionResponse(
    UUID reportId,
    UUID versionId,
    int versionNumber,
    UUID inspectionId,
    UUID authorUserId,
    UUID sourceVersionId,
    ReportStatus reportStatus,
    ReportStatus versionStatus,
    ReportSnapshot contentSnapshot,
    ReviewSummary review,
    Instant createdAt,
    Instant releasedAt,
    Instant acceptedAt,
    UUID clientDecisionByUserId,
    String clientDecisionReason) {

  public record ReviewSummary(
      UUID reviewerUserId, PeerReviewDecision decision, String comments, Instant reviewedAt) {}
}
