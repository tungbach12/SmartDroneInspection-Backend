package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

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
