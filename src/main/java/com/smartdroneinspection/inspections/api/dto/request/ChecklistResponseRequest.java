package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record ChecklistResponseRequest(
    @NotNull JsonNode responseValue, @Size(max = 4000) String notes) {}
