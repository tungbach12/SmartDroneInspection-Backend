package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * What MF2-09 to MF2-11 show about one field session.
 *
 * <p>{@code readinessDecisionId} is part of the response because it is the record of which approval
 * this session relied on. {@code startedAt} is the software's session start, not hardware flight
 * time, and the two are deliberately the same field rather than one implying the other.
 */
public record FieldSessionResponse(
    UUID id,
    UUID inspectionId,
    UUID organizationId,
    UUID inspectorUserId,
    UUID droneId,
    UUID readinessDecisionId,
    UUID checklistTemplateId,
    Integer readinessVersion,
    String status,
    Instant startedAt,
    Instant endedAt,
    String postponementReason,
    String abortReason) {}
