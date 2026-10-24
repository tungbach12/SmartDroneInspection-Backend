package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/** Version contract with the current submission and reviewer decision. */
public record InspectionReportVersionResponse(
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
    String omissionDisclosure,
    String contentSnapshot,
    ReportSectionsResponse sections,
    InspectionReportContextResponse reportContext) {}
