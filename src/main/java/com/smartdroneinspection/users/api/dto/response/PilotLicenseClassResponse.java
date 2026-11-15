package com.smartdroneinspection.users.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PilotLicenseClassResponse(
    UUID id,
    String name,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
