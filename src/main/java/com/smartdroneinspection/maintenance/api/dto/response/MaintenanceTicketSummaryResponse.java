package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.domain.enums.ResolutionDecision;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceTicketSummaryResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    UUID acceptedReportVersionId,
    MaintenancePriority priority,
    MaintenanceTicketStatus status,
    ResolutionDecision resolutionDecision,
    Instant preferredDeadline,
    Instant createdAt,
    Instant acceptedAt,
    Instant closedAt) {}
