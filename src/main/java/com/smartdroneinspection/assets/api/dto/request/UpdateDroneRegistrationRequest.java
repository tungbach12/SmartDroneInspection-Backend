package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateDroneRegistrationRequest(
    @Size(max = 100) String registrationNumber,
    @Size(max = 1000) String registrationFileKey,
    Instant registrationExpiresAt) {}
