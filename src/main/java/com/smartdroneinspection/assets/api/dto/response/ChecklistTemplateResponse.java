package com.smartdroneinspection.assets.api.dto.response;

import java.util.UUID;

public record ChecklistTemplateResponse(
    UUID id, String templateType, UUID categoryId, String label, int sortOrder, boolean active) {}
