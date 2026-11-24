package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.ChecklistItemSnapshot;
import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.PreFlightConditions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FieldSessionResponse(
    UUID id,
    UUID inspectionId,
    String status,
    String preFlightChecklistNote,
    Instant startedAt,
    Instant endedAt,
    String postponementReason,
    String abortReason,
    String outcomeNotes,
    UUID flightVideoEvidenceId,
    String telemetrySource,
    boolean hasFlightReport,
    LocalDate sessionDate,
    List<ChecklistItemSnapshot> preFlightChecklistSnapshot,
    PreFlightConditions preFlightConditions,
    List<ChecklistItemSnapshot> postFlightChecklistSnapshot,
    List<FieldSessionFlightResponse> flights) {

  public FieldSessionResponse {
    preFlightChecklistSnapshot =
        preFlightChecklistSnapshot == null ? List.of() : List.copyOf(preFlightChecklistSnapshot);
    postFlightChecklistSnapshot =
        postFlightChecklistSnapshot == null ? List.of() : List.copyOf(postFlightChecklistSnapshot);
    flights = flights == null ? List.of() : List.copyOf(flights);
  }

  public FieldSessionResponse(
      UUID id,
      UUID inspectionId,
      String status,
      String preFlightChecklistNote,
      Instant startedAt,
      Instant endedAt,
      String postponementReason,
      String abortReason,
      String outcomeNotes) {
    this(
        id,
        inspectionId,
        status,
        preFlightChecklistNote,
        startedAt,
        endedAt,
        postponementReason,
        abortReason,
        outcomeNotes,
        null,
        null,
        false,
        null,
        List.of(),
        null,
        List.of(),
        List.of());
  }

  public FieldSessionResponse(
      UUID id,
      UUID inspectionId,
      String status,
      String preFlightChecklistNote,
      Instant startedAt,
      Instant endedAt,
      String postponementReason,
      String abortReason,
      String outcomeNotes,
      UUID flightVideoEvidenceId,
      String telemetrySource,
      boolean hasFlightReport) {
    this(
        id,
        inspectionId,
        status,
        preFlightChecklistNote,
        startedAt,
        endedAt,
        postponementReason,
        abortReason,
        outcomeNotes,
        flightVideoEvidenceId,
        telemetrySource,
        hasFlightReport,
        null,
        List.of(),
        null,
        List.of(),
        List.of());
  }
}
