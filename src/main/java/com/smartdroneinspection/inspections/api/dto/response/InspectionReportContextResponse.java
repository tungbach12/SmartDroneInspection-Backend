package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
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
    PreparationContext preparation,
    EvidenceQualityContext evidenceQuality,
    List<FieldSessionContext> fieldSessions,
    String reviewerQualificationDisclosure,
    String findingsConfirmedBy,
    Instant findingsConfirmedAt,
    String findingsSnapshotHash) {

  public InspectionReportContextResponse {
    fieldSessions = fieldSessions == null ? List.of() : List.copyOf(fieldSessions);
  }

  public record DroneContext(String serialNumber, String manufacturer, String model) {}

  public record PreparationContext(
      int version,
      String status,
      String shotList,
      String evidenceTypes,
      String accessConstraints,
      String safetyObservations,
      String readinessDecision,
      Instant readinessDecisionAt,
      String readinessReason,
      String permitDocumentReferences,
      Instant submittedAt) {}

  public record EvidenceQualityContext(
      String decision,
      String shotListComparison,
      String limitationReason,
      UUID decidedByUserId,
      Instant decidedAt) {}

  public record FieldSessionContext(
      UUID id,
      String status,
      Instant startedAt,
      Instant endedAt,
      String preFlightChecklistNote,
      String postponementReason,
      String abortReason,
      String outcomeNotes,
      String limitations,
      String droneSerialNumber,
      String droneManufacturer,
      String droneModel) {}
}
