package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpsertInspectorRatingRequest(
    @NotNull(message = "overallScore is required")
        @Min(value = 1, message = "overallScore must be between 1 and 5")
        @Max(value = 5, message = "overallScore must be between 1 and 5")
        Integer overallScore,
    @NotNull(message = "criterionScores is required") Map<String, Integer> criterionScores,
    @Size(max = 2000, message = "comment must not exceed 2000 characters") String comment) {}
