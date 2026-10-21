package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReassignPairRequest(
    @NotNull UUID inspectorId, @NotNull UUID droneId, String reason) {}
