package com.smartdroneinspection.shared.api;

import java.math.BigDecimal;

/** Shared port exposing airspace classification and ceiling rules across modules. */
public interface AirspaceZoneAdvisor {
  AirspaceZoneInfo evaluateZone(BigDecimal latitude, BigDecimal longitude);
}
