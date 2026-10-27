package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AirspaceEvaluationResponse(
    BigDecimal latitude,
    BigDecimal longitude,
    String classification,
    String messageCode,
    String advisoryMessage,
    String portalUrl,
    List<AirspaceZoneResponse> matchingZones,
    List<AirspaceZoneResponse> nearbyZones) {

  public AirspaceEvaluationResponse {
    matchingZones = matchingZones == null ? List.of() : List.copyOf(matchingZones);
    nearbyZones = nearbyZones == null ? List.of() : List.copyOf(nearbyZones);
  }
}
