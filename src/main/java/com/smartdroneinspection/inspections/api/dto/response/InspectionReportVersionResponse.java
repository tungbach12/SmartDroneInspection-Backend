package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A report version with its human gates: who verified, who reviewed, and when it published.
 *
 * <p>The narrative and the omission disclosure are carried here so a reviewer reads the document
 * they are approving. They were persisted in {@code content_snapshot} from the first version, but
 * the response omitted them, so no client could ever display what it was signing off.
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
    String narrative,
    String omissionDisclosure,
    String contentSnapshot,
    ReportSectionsResponse sections) {}
