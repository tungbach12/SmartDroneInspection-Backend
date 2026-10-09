package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/** A report version with its human gates: who verified, who reviewed, and when it published. */
public record InspectionReportVersionResponse(
    UUID id,
    UUID inspectionReportId,
    int versionNo,
    ReportStatus status,
    UUID authorUserId,
    Instant authorVerifiedAt,
    UUID reviewerUserId,
    Instant reviewedAt,
    String reviewReason,
    String llmModel,
    String promptVersion,
    Instant generatedAt,
    String evidenceSnapshotHash,
    Instant publishedAt,
    Instant createdAt) {}
