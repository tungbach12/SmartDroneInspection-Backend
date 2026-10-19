package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A report version with its author and report-level review gates. Structured authored sections are
 * exposed in {@code sections}; legacy narrative keys remain in old content snapshots for read
 * compatibility but are not part of the client contract.
 */
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
    com.smartdroneinspection.inspections.domain.enums.ReportReturnReasonType returnReasonType,
    UUID withdrawnByUserId,
    Instant withdrawnAt,
    String withdrawalReason,
    String llmModel,
    String promptVersion,
    Instant generatedAt,
    String evidenceSnapshotHash,
    String findingsSnapshotHash,
    Instant publishedAt,
    Instant createdAt,
    String omissionDisclosure,
    String contentSnapshot,
    ReportSectionsResponse sections,
    InspectionReportContextResponse reportContext) {}
