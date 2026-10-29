package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterPartnerResourceRequest(@NotNull UUID resourceUserId, @NotNull UUID droneId) {}
