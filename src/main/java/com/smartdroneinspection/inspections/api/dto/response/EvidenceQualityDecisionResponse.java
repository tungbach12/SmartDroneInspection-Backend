package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.EvidenceQualityDecisionType;
import java.time.Instant;
import java.util.UUID;

public record EvidenceQualityDecisionResponse(
    UUID id,
    UUID inspectionId,
    UUID fieldSessionId,
    EvidenceQualityDecisionType decision,
    String shotListComparison,
    String limitationReason,
    UUID decidedByUserId,
    Instant decidedAt) {}
