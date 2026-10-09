package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A model suggestion. Never an official finding on its own; model name and version travel with it
 * so the provenance of the suggestion stays visible.
 */
public record AiFindingCandidateResponse(
    UUID id,
    UUID evidenceId,
    String modelName,
    String modelVersion,
    String predictedLabel,
    BigDecimal confidence,
    String boundingBox,
    AiFindingCandidateStatus status,
    UUID reviewedByUserId,
    Instant reviewedAt,
    String rejectionReason,
    Instant createdAt) {}
