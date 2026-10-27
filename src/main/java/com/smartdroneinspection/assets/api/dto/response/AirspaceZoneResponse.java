package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;

public record AirspaceZoneResponse(
    String code,
    String name,
    String region,
    String zoneType,
    BigDecimal latitude,
    BigDecimal longitude,
    int radiusMeters,
    int maxAltitudeMetersAgl,
    String authority,
    String legalBasis,
    String description,
    Long distanceMeters) {

  public AirspaceZoneResponse withDistanceMeters(long computedDistanceMeters) {
    return new AirspaceZoneResponse(
        code,
        name,
        region,
        zoneType,
        latitude,
        longitude,
        radiusMeters,
        maxAltitudeMetersAgl,
        authority,
        legalBasis,
        description,
        computedDistanceMeters);
  }
}
