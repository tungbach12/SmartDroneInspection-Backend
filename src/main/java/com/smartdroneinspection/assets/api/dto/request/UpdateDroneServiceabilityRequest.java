package com.smartdroneinspection.assets.api.dto.request;

import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import jakarta.validation.constraints.NotNull;

/**
 * Requested serviceability change. The value names a maintenance state only; it is not a claim that
 * the airframe is permitted to fly.
 */
public record UpdateDroneServiceabilityRequest(@NotNull DroneServiceability serviceability) {}
