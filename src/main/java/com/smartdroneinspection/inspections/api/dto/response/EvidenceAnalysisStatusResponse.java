package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EvidenceAnalysisStatusResponse(
    UUID evidenceId, Instant completedAt, int detectionCount) {}
