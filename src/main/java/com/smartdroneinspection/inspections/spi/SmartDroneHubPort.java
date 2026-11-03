package com.smartdroneinspection.inspections.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Inspection-owned outbound contract for read-only drone telemetry and demo mission metadata. */
public interface SmartDroneHubPort {

  Mission createMission(MissionRequest request);

  Telemetry missionTelemetry(UUID inspectionId, UUID fieldSessionId);

  FlightReport flightReport(UUID inspectionId, UUID fieldSessionId, Instant endedAt);

  default CapturedMedia missionRecording(UUID inspectionId, UUID fieldSessionId) {
    throw new UnsupportedOperationException("Mission recording is not supported by this adapter");
  }

  default CapturedMedia captureSnapshot(UUID inspectionId, UUID fieldSessionId) {
    throw new UnsupportedOperationException("Snapshot capture is not supported by this adapter");
  }

  record CapturedMedia(
      String fileName,
      String contentType,
      byte[] content,
      Instant capturedAt,
      BigDecimal latitude,
      BigDecimal longitude,
      String locationDescription) {}

  record MissionRequest(
      UUID inspectionId,
      UUID fieldSessionId,
      String assetName,
      BigDecimal latitude,
      BigDecimal longitude,
      List<String> inspectionItems,
      String telemetrySource) {}

  record Mission(
      UUID inspectionId,
      UUID fieldSessionId,
      String missionState,
      String telemetrySource,
      boolean connected,
      java.time.Instant lastUpdateAt,
      BigDecimal latitude,
      BigDecimal longitude,
      Double altitudeMeters,
      Double groundSpeedMps,
      Double headingDeg,
      Integer batteryPercent,
      String flightMode,
      Boolean armed,
      Integer currentWaypoint,
      double distanceFlownMeters) {}

  record Telemetry(
      UUID inspectionId,
      UUID fieldSessionId,
      String missionState,
      String telemetrySource,
      boolean connected,
      java.time.Instant lastUpdateAt,
      BigDecimal latitude,
      BigDecimal longitude,
      Double altitudeMeters,
      Double groundSpeedMps,
      Double headingDeg,
      Integer batteryPercent,
      String flightMode,
      Boolean armed,
      Integer currentWaypoint,
      double distanceFlownMeters) {}

  record FlightTracePoint(
      double latitude,
      double longitude,
      Double altitudeMeters,
      Double groundSpeedMps,
      Double headingDeg,
      Integer waypoint,
      java.time.Instant recordedAt) {}

  record FlightReport(
      UUID inspectionId,
      UUID fieldSessionId,
      String telemetrySource,
      java.time.Instant startedAt,
      java.time.Instant endedAt,
      int durationSeconds,
      double distanceFlownMeters,
      Double maxAltitudeMeters,
      Integer startBatteryPercent,
      Integer endBatteryPercent,
      String finalState,
      String note,
      List<FlightTracePoint> flightTrace,
      UUID flightVideoEvidenceId) {

    public FlightReport(
        UUID inspectionId,
        UUID fieldSessionId,
        String telemetrySource,
        java.time.Instant startedAt,
        java.time.Instant endedAt,
        int durationSeconds,
        double distanceFlownMeters,
        Double maxAltitudeMeters,
        Integer startBatteryPercent,
        Integer endBatteryPercent,
        String finalState,
        String note) {
      this(
          inspectionId,
          fieldSessionId,
          telemetrySource,
          startedAt,
          endedAt,
          durationSeconds,
          distanceFlownMeters,
          maxAltitudeMeters,
          startBatteryPercent,
          endBatteryPercent,
          finalState,
          note,
          List.of(),
          null);
    }
  }
}
