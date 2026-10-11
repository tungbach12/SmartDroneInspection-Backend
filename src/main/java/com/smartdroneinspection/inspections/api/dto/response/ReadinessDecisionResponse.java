package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import java.time.Instant;
import java.util.UUID;

/**
 * What MF2-07 records about one readiness decision.
 *
 * <p>{@code sourceHash} and {@code decidedAt} are part of the response because the decision is the
 * evidence a later field-session start re-checks; a reviewer needs to be able to quote which
 * decision they took. The snapshots themselves stay server-side: they are large, and no client
 * should be reconstructing the source basis from what this returns.
 */
public record ReadinessDecisionResponse(
    UUID id,
    UUID inspectionId,
    UUID preparationId,
    Integer preparationVersion,
    ReadinessDecisionType decision,
    UUID reviewedByUserId,
    String reason,
    String sourceHash,
    Instant decidedAt) {}
