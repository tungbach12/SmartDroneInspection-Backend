package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.domain.enums.ResolutionDecision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaintenanceTicketDetailResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    UUID acceptedReportVersionId,
    UUID createdByUserId,
    MaintenancePriority priority,
    Instant preferredDeadline,
    String instructions,
    MaintenanceTicketStatus status,
    ResolutionDecision resolutionDecision,
    Instant acceptedAt,
    Instant warrantyStartedAt,
    Instant warrantyEndsAt,
    Instant releasedAt,
    Instant closedAt,
    Instant createdAt,
    Instant updatedAt,
    List<UUID> findingIds) {}
