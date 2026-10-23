package com.smartdroneinspection.maintenance.api.dto.request;

import java.util.UUID;

/** Request payload for generating RAG-assisted repair scope and acceptance criteria. */
public record SuggestScopeRequest(
    UUID sourceReportVersionId,
    UUID sourceFindingId,
    String findingCode,
    String defectLabel,
    String description,
    String severity,
    String recommendedAction,
    String component,
    String locationDescription,
    String measurement) {}
