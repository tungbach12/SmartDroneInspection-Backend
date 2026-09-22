package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ChecklistResponseResponse(
    UUID responseId,
    UUID inspectionId,
    UUID checklistItemId,
    JsonNode responseValue,
    String notes,
    UUID completedByUserId,
    Instant completedAt) {}
