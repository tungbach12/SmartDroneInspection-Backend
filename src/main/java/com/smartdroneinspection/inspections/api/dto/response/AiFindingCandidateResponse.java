package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiFindingCandidateResponse(
    UUID id,
    UUID evidenceId,
    String modelName,
    String modelVersion,
    String predictedLabel,
    BigDecimal confidence,
    String boundingBox,
    AiFindingCandidateStatus status,
    Instant createdAt) {}
