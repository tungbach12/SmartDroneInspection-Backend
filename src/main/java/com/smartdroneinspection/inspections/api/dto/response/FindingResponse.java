package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A finding as the API returns it, including its current MF4 forwarding state. */
public record FindingResponse(
    UUID id,
    UUID inspectionId,
    UUID evidenceId,
    String findingCode,
    String defectLabel,
    FindingSeverity severity,
    String boundingBox,
    String technicalNotes,
    String recommendedAction,
    FindingStatus status,
    String modelName,
    BigDecimal confidence,
    Instant forwardedToMaintenanceAt,
    boolean isDeleted,
    Instant createdAt) {}
