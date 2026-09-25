package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

public record ReportAcceptedEvent(
    UUID reportId,
    UUID reportVersionId,
    UUID inspectionId,
    UUID organizationId,
    UUID assetId,
    UUID acceptedByUserId,
    Instant acceptedAt) {}
