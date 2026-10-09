package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MF3-13 publication outcome. An empty {@code repairRequiredFindingIds} means no corrective work is
 * required within the observed scope — not a failed handoff.
 */
public record ReportPublishedResponse(
    UUID reportVersionId,
    int versionNo,
    Instant publishedAt,
    List<UUID> repairRequiredFindingIds,
    InspectionStatus inspectionStatus) {}
