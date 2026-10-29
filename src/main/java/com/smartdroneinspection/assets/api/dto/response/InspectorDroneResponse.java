package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.Drone;
import java.util.UUID;

public record InspectorDroneResponse(
    UUID id, String serialNumber, String model, String manufacturer, String serviceability) {
  public static InspectorDroneResponse from(Drone drone) {
    return new InspectorDroneResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getServiceability().name());
  }
}
