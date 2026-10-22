package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * One checklist item as the mobile client reads it.
 *
 * <p>The section, prompt and guidance are system-managed text, so they arrive in the locale the
 * request asked for. The response value, the note and the completion time are the Inspector's own
 * record and are returned unchanged.
 */
public record InspectionChecklistItemResponse(
    UUID itemId,
    String itemCode,
    String sectionName,
    String prompt,
    String responseType,
    boolean required,
    int displayOrder,
    String guidance,
    String validationConfig,
    JsonNode responseValue,
    String notes,
    Instant completedAt) {}
