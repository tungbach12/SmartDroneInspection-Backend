package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.Drone;
import java.util.UUID;

/**
 * A candidate airframe offered for selection.
 *
 * <p>Serviceability is a maintenance fact. A candidate carries no permit, clearance or readiness
 * signal: eligibility to fly is decided by the permit and readiness flows, never by this payload.
 */
public record DroneSelectionResponse(
    UUID id, String serialNumber, String model, String manufacturer, String serviceability) {

  public static DroneSelectionResponse from(Drone drone) {
    return new DroneSelectionResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getServiceability().name());
  }
}
