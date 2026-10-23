package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * The Inspector's answer to one checklist item.
 *
 * <p>The note is optional and is stored exactly as written, in the Inspector's own language.
 */
public record ChecklistResponseRequest(
    @NotNull JsonNode responseValue, @Size(max = 4000) String notes) {}
