package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MF3-11/13 report outcome. Forwarded finding IDs are selected after publication; an empty list
 * means that no findings were forwarded by this action.
 */
public record ReportPublishedResponse(
    UUID reportVersionId,
    int versionNo,
    Instant publishedAt,
    List<UUID> forwardedFindingIds,
    InspectionStatus inspectionStatus) {}
