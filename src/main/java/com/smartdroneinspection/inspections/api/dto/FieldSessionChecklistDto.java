package com.smartdroneinspection.inspections.api.dto;

import java.util.List;

/** Snapshot records for pre-/post-flight safety checklists and on-site readiness conditions. */
public final class FieldSessionChecklistDto {

  private FieldSessionChecklistDto() {}

  public record ChecklistItemSnapshot(
      String code,
      String category,
      String label,
      String details,
      String referenceThreshold,
      boolean safetyCritical,
      boolean required,
      boolean checked) {}

  public record PreFlightConditions(
      Double windSpeedMs,
      Double ambientTempC,
      Double visibilityKm,
      Integer minBatteryPercent,
      Integer gpsSatellites,
      Double rthAltitudeMeters,
      Double safetyPerimeterMeters,
      String weatherSummary) {}

  public static List<ChecklistItemSnapshot> safeList(List<ChecklistItemSnapshot> items) {
    return items == null ? List.of() : List.copyOf(items);
  }
}
