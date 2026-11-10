package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
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
    boolean hasFlightReport) {

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
        false);
  }
}
