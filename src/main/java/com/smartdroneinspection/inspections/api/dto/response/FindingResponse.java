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
    FindingStatus status,
    String findingCode,
    String defectLabel,
    FindingSeverity severity,
    String technicalNotes,
    String recommendedAction,
    String boundingBox,
    String modelName,
    String modelVersion,
    String modelProvider,
    BigDecimal confidence,
    Instant inspectorConfirmedAt,
    Instant forwardedToMaintenanceAt,
    Instant deletedAt) {}
