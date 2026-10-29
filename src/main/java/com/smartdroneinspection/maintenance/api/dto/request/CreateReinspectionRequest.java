package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateReinspectionRequest(
    int maintenanceReportVersionNo,
    @NotEmpty List<UUID> findingIds,
    @Size(max = 4000) String note) {}
