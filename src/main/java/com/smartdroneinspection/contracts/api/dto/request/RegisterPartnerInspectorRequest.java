package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterPartnerInspectorRequest(@NotNull UUID inspectorUserId) {}
