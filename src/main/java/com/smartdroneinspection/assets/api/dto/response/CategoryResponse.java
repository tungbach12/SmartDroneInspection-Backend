package com.smartdroneinspection.assets.api.dto.response;

import java.util.UUID;

public record CategoryResponse(
    UUID id, String code, String name, String description, boolean active) {}
