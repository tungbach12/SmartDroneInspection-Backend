package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceWorkLogResponse(
    UUID id,
    UUID maintenanceTicketId,
    UUID executionAssignmentId,
    UUID engineerUserId,
    Instant startedAt,
    Instant endedAt,
    BigDecimal progressPercent,
    String workSummary,
    String materialsUsed,
    BigDecimal laborHours,
    BigDecimal actualCost,
    String currency,
    WorkLogStatus status,
    UUID beforeEvidenceId,
    UUID afterEvidenceId,
    Instant submittedAt,
    UUID verifiedByUserId,
    Instant verifiedAt,
    Instant createdAt) {}
