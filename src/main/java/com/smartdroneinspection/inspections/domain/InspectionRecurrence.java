package com.smartdroneinspection.inspections.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Calendar recurrence anchored to the original local date, never to a shortened prior month. */
public record InspectionRecurrence(
    LocalDateTime anchor, String timezone, String unit, int interval) {

  public InspectionRecurrence {
    Objects.requireNonNull(anchor, "Recurrence anchor is required");
    ZoneId.of(Objects.requireNonNull(timezone, "Recurrence timezone is required"));
    if (!"WEEK".equals(unit) && !"MONTH".equals(unit) && !"YEAR".equals(unit)) {
      throw new IllegalArgumentException("Recurrence unit must be WEEK, MONTH or YEAR");
    }
    if (interval < 1 || interval > 120) {
      throw new IllegalArgumentException("Recurrence interval must be between 1 and 120");
    }
  }

  public Instant occurrenceAt(long occurrence) {
    if (occurrence < 0) {
      throw new IllegalArgumentException("Occurrence index must not be negative");
    }
    long offset = Math.multiplyExact(occurrence, interval);
    LocalDateTime date =
        switch (unit) {
          case "WEEK" -> anchor.plusWeeks(offset);
          case "MONTH" -> anchor.plusMonths(offset);
          case "YEAR" -> anchor.plusYears(offset);
          default -> throw new IllegalStateException("Unsupported recurrence unit");
        };
    // Java resolves a DST gap forward and an overlap with the earlier offset deterministically.
    return date.atZone(ZoneId.of(timezone)).toInstant();
  }
}
