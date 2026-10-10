package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * MF4-01/02: opens a work order for one confirmed repair-required finding on a published report.
 *
 * <p>The finding and report version are named explicitly rather than derived on the server, so the
 * administrator records exactly which published finding this corrective work answers.
 */
public record CreateWorkOrderRequest(
    @NotNull UUID assetId,
    @NotNull UUID sourceReportVersionId,
    @NotNull UUID sourceFindingId,
    @NotNull UUID budgetApproverUserId,
    MaintenancePriority priority,
    Instant dueAt,
    @NotBlank String correctiveScope,
    String acceptanceCriteria) {}
