package com.smartdroneinspection.assets.api.dto.request;

import com.smartdroneinspection.assets.domain.enums.DroneDocumentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDroneDocumentStatusRequest(@NotNull DroneDocumentStatus status) {}
