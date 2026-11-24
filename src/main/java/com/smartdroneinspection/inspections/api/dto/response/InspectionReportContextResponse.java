package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.ChecklistItemSnapshot;
import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.PreFlightConditions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Source facts attached to the current report version for authoring and reviewer display. */
public record InspectionReportContextResponse(
    String organizationName,
    String assetCode,
    String assetName,
    String assetLocation,
    String assetCategory,
    DroneContext assignedDrone,
    List<FieldSessionContext> fieldSessions,
    String reviewerQualificationDisclosure,
    Instant findingsConfirmedAt,
    List<FieldSessionFlightResponse> flights) {

  public InspectionReportContextResponse {
    fieldSessions = fieldSessions == null ? List.of() : List.copyOf(fieldSessions);
    flights = flights == null ? List.of() : List.copyOf(flights);
  }

  public InspectionReportContextResponse(
      String organizationName,
      String assetCode,
      String assetName,
      String assetLocation,
      String assetCategory,
      DroneContext assignedDrone,
      List<FieldSessionContext> fieldSessions,
      String reviewerQualificationDisclosure,
      Instant findingsConfirmedAt) {
    this(
        organizationName,
        assetCode,
        assetName,
        assetLocation,
        assetCategory,
        assignedDrone,
        fieldSessions,
        reviewerQualificationDisclosure,
        findingsConfirmedAt,
        List.of());
  }

  public record DroneContext(String serialNumber, String manufacturer, String model) {}

  public record FieldSessionContext(
      UUID id,
      String status,
      Instant startedAt,
      Instant endedAt,
      String preFlightChecklistNote,
      String postponementReason,
      String abortReason,
      String outcomeNotes,
      String droneSerialNumber,
      String droneManufacturer,
      String droneModel,
      LocalDate sessionDate,
      List<ChecklistItemSnapshot> preFlightChecklistSnapshot,
      PreFlightConditions preFlightConditions,
      List<ChecklistItemSnapshot> postFlightChecklistSnapshot) {

    public FieldSessionContext {
      preFlightChecklistSnapshot =
          preFlightChecklistSnapshot == null ? List.of() : List.copyOf(preFlightChecklistSnapshot);
      postFlightChecklistSnapshot =
          postFlightChecklistSnapshot == null
              ? List.of()
              : List.copyOf(postFlightChecklistSnapshot);
    }

    public FieldSessionContext(
        UUID id,
        String status,
        Instant startedAt,
        Instant endedAt,
        String preFlightChecklistNote,
        String postponementReason,
        String abortReason,
        String outcomeNotes,
        String droneSerialNumber,
        String droneManufacturer,
        String droneModel) {
      this(
          id,
          status,
          startedAt,
          endedAt,
          preFlightChecklistNote,
          postponementReason,
          abortReason,
          outcomeNotes,
          droneSerialNumber,
          droneManufacturer,
          droneModel,
          null,
          List.of(),
          null,
          List.of());
    }
  }
}
