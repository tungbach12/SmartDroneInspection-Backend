package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.MaintenanceAssignmentType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AssignMaintenanceEngineerRequest(
    @NotNull UUID engineerUserId,
    @NotNull MaintenanceAssignmentType assignmentType,
    Instant deadline) {}
