package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** The saved checklist answer, returned with the Inspector's note unchanged. */
public record ChecklistResponseResponse(
    UUID responseId,
    UUID inspectionId,
    UUID checklistItemId,
    JsonNode responseValue,
    String notes,
    Instant completedAt) {}
