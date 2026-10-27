package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateRecurringScheduleRequest(
    @NotNull UUID assetId,
    @NotNull LocalDateTime anchor,
    @NotBlank String timezone,
    @NotBlank String unit,
    @NotNull @Min(1) @Max(120) Integer interval,
    @NotNull @Min(1) @Max(604800) Long durationSeconds,
    @Min(0) @Max(30) Integer reminderDays,
    Instant endsAt,
    UUID inspectorId,
    UUID droneId) {

  public int resolvedReminderDays() {
    return reminderDays != null ? reminderDays : 3;
  }
}
