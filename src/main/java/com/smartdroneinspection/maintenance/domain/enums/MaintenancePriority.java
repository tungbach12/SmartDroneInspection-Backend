package com.smartdroneinspection.maintenance.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum MaintenancePriority {
  LOW,
  NORMAL,
  HIGH,
  URGENT;

  @JsonCreator
  public static MaintenancePriority fromValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return switch (raw.trim().toUpperCase(Locale.ROOT)) {
      case "MEDIUM" -> NORMAL;
      case "CRITICAL" -> URGENT;
      default -> MaintenancePriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    };
  }
}
