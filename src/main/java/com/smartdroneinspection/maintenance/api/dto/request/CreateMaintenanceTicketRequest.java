package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.DispatchMode;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateMaintenanceTicketRequest(
    @NotNull UUID assetId,
    @NotNull UUID acceptedReportVersionId,
    @NotEmpty List<UUID> findingIds,
    @NotNull MaintenancePriority priority,
    Instant preferredDeadline,
    @Size(max = 4000) String instructions,
    DispatchMode dispatchMode) {}
