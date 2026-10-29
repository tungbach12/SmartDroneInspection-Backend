package com.smartdroneinspection.inspections.spi;

import java.time.Instant;
import java.util.UUID;

public record ReinspectionInspectionAssignment(
    UUID organizationId,
    UUID assetId,
    UUID inspectorId,
    UUID droneId,
    UUID managerUserId,
    Instant plannedStartAt,
    Instant plannedEndAt) {}
