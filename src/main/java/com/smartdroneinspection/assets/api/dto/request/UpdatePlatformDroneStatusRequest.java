package com.smartdroneinspection.assets.api.dto.request;

import com.smartdroneinspection.assets.domain.enums.PlatformDroneStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePlatformDroneStatusRequest(
    @NotNull PlatformDroneStatus status, @Size(max = 2000) String notes) {}
