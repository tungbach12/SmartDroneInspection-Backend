package com.smartdroneinspection.shared.api;

/** Cross-module summary of the controlling airspace zone for an asset's coordinates. */
public record AirspaceZoneInfo(
    CustomerAirspaceStatus status,
    String zoneCode,
    String zoneName,
    String authority,
    Integer maxAltitudeMeters,
    String advisoryMessage) {

  public AirspaceZoneInfo {
    if (status == null) {
      status = CustomerAirspaceStatus.UNKNOWN;
    }
  }
}
