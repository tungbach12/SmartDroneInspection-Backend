package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A finding as the API hands it back.
 *
 * <p>{@code status} says whether the Inspector has confirmed it, and {@code deletedAt} says whether
 * it was removed. The model provenance travels with it when there is one, so a caller can see which
 * suggestions came from the detector without the workflow depending on that distinction.
 */
public record FindingResponse(
    UUID id,
    UUID inspectionId,
    UUID evidenceId,
    FindingStatus status,
    String findingCode,
    String defectLabel,
    FindingSeverity severity,
    String locationDescription,
    String technicalNotes,
    String recommendedAction,
    String component,
    String description,
    String observedCondition,
    String priority,
    String measurement,
    String boundingBox,
    String modelName,
    String modelVersion,
    String modelProvider,
    BigDecimal confidence,
    Instant inspectorConfirmedAt,
    boolean repairRequired,
    boolean correctiveActionRequired,
    String correctiveActionPriority,
    Instant correctiveActionDueAt,
    UUID correctiveActionReviewedByUserId,
    Instant correctiveActionReviewedAt,
    String correctiveActionRationale,
    Instant deletedAt,
    UUID deletedByUserId,
    String deletionReason) {}
