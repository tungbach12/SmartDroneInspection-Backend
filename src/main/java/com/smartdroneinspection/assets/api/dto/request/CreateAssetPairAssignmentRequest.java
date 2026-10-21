package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateAssetPairAssignmentRequest(
    @NotNull UUID inspectorUserId,
    @NotNull UUID droneId,
    @NotNull Instant validFrom,
    Instant validUntil,
    @Size(max = 2000) String reason) {}
