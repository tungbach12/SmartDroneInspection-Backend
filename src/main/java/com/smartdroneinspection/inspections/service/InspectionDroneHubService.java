package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant- and assignment-scoped bridge from an Inspector session to telemetry-only SmartDroneHub.
 */
@Service
public class InspectionDroneHubService {

  private final InspectionRepository inspections;
  private final FieldSessionRepository sessions;
  private final AssetRepository assets;
  private final UserAccess userAccess;
  private final SmartDroneHubPort hub;
  private final EvidenceService evidenceService;
  private final FlightLogParser flightLogParser;

  public InspectionDroneHubService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      AssetRepository assets,
      UserAccess userAccess,
      SmartDroneHubPort hub) {
    this(inspections, sessions, assets, userAccess, hub, null, null);
  }

  @Autowired
  public InspectionDroneHubService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      AssetRepository assets,
      UserAccess userAccess,
      SmartDroneHubPort hub,
      EvidenceService evidenceService,
      FlightLogParser flightLogParser) {
    this.inspections = inspections;
    this.sessions = sessions;
    this.assets = assets;
    this.userAccess = userAccess;
    this.hub = hub;
    this.evidenceService = evidenceService;
    this.flightLogParser = flightLogParser;
  }

  @Transactional(readOnly = true)
  public SmartDroneHubPort.Mission start(
      UUID actorId, UUID inspectionId, UUID sessionId, String telemetrySource) {
    Scope scope = requireInspectorSession(actorId, inspectionId, sessionId);
    if (!scope.inspection().hasFlightPermit()) {
      throw failure(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_REQUIRED",
          "Upload the mission flight permit before starting the Hub session.");
    }
    if (scope.inspection().getStatus() != InspectionStatus.IN_PROGRESS
        || !scope.session().isInProgress()) {
      throw failure(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_IN_PROGRESS",
          "The field session must be active before connecting SmartDroneHub.");
    }
    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(
                scope.inspection().getAssetId(), scope.inspection().getOwnerCustomerId())
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found."));
    SmartDroneHubPort.MissionRequest request =
        new SmartDroneHubPort.MissionRequest(
            inspectionId,
            sessionId,
            asset.getName(),
            asset.getLatitude(),
            asset.getLongitude(),
            asset.getInspectionItems(),
            normalizeTelemetrySource(telemetrySource));
    return hub.createMission(request);
  }

  @Transactional
  public SmartDroneHubPort.FlightReport importFlightLog(
      UUID actorId, UUID inspectionId, UUID sessionId, String fileName, InputStream input) {
    if (fileName == null || fileName.length() > 255) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "FLIGHT_LOG_FORMAT_UNSUPPORTED",
          "The flight log filename is invalid.");
    }
    Scope scope = requireInspectorSession(actorId, inspectionId, sessionId);
    requireActiveFlightSession(scope);
    String extension = extension(fileName);
    List<SmartDroneHubPort.FlightTracePoint> trace =
        switch (extension) {
          case "srt" -> flightLogParser.parseSrt(input);
          case "csv" -> flightLogParser.parseCsv(input);
          case "kml" -> flightLogParser.parseKml(input);
          case "gpx" -> flightLogParser.parseGpx(input);
          default ->
              throw failure(
                  HttpStatus.BAD_REQUEST,
                  "FLIGHT_LOG_FORMAT_UNSUPPORTED",
                  "Supported flight log formats are SRT, CSV, KML, and GPX.");
        };
    var timedPoints = trace.stream().filter(point -> point.recordedAt() != null).toList();
    Instant firstTime = timedPoints.size() >= 2 ? timedPoints.getFirst().recordedAt() : null;
    Instant lastTime = timedPoints.size() >= 2 ? timedPoints.getLast().recordedAt() : null;
    Integer duration =
        firstTime != null && lastTime != null
            ? (int) Math.max(0, Duration.between(firstTime, lastTime).getSeconds())
            : null;
    Double distance = trace.size() < 2 ? null : 0d;
    for (int i = 1; i < trace.size(); i++) {
      var prev = trace.get(i - 1);
      var next = trace.get(i);
      distance +=
          haversineMeters(prev.latitude(), prev.longitude(), next.latitude(), next.longitude());
    }
    Double maxAltitude =
        trace.stream()
            .map(SmartDroneHubPort.FlightTracePoint::altitudeMeters)
            .filter(java.util.Objects::nonNull)
            .max(Double::compareTo)
            .orElse(null);
    Integer startBattery =
        trace.stream()
            .map(SmartDroneHubPort.FlightTracePoint::batteryPercent)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    Integer endBattery =
        trace.stream()
            .map(SmartDroneHubPort.FlightTracePoint::batteryPercent)
            .filter(java.util.Objects::nonNull)
            .reduce((a, b) -> b)
            .orElse(null);
    String source = "IMPORTED_LOG_" + extension.toUpperCase(java.util.Locale.ROOT);
    String proximityWarning = "";
    var assetOpt =
        assets.findByIdAndOwnerCustomerId(
            scope.inspection().getAssetId(), scope.inspection().getOwnerCustomerId());
    if (assetOpt.isPresent()) {
      Asset asset = assetOpt.get();
      if (asset.getLatitude() != null && asset.getLongitude() != null) {
        double gapMeters =
            haversineMeters(
                trace.getFirst().latitude(),
                trace.getFirst().longitude(),
                asset.getLatitude().doubleValue(),
                asset.getLongitude().doubleValue());
        if (gapMeters > 5000d) {
          proximityWarning =
              String.format(
                  java.util.Locale.ROOT,
                  " [WARN: First GPS point is %.1f km from registered asset coordinates.]",
                  gapMeters / 1000d);
        }
      }
    }
    String note =
        "Imported flight log: "
            + safeFileName(fileName)
            + ". Flight trace parsed from the uploaded "
            + extension.toUpperCase(java.util.Locale.ROOT)
            + " file; unavailable telemetry fields are not inferred."
            + proximityWarning;
    scope
        .session()
        .recordFlightReport(
            source,
            duration,
            distance,
            maxAltitude,
            startBattery,
            endBattery,
            "UNKNOWN",
            note,
            serializeTrace(trace));
    sessions.saveAndFlush(scope.session());
    return toPersistedFlightReport(scope.session());
  }

  @Transactional
  public SmartDroneHubPort.FlightReport declareManualFlight(
      UUID actorId,
      UUID inspectionId,
      UUID sessionId,
      String reasonCode,
      int durationMinutes,
      Double maxAltitudeMeters,
      String notes) {
    Scope scope = requireInspectorSession(actorId, inspectionId, sessionId);
    requireActiveFlightSession(scope);
    if (!List.of("NO_MAVLINK_SUPPORT", "PROPRIETARY_APP", "TELEMETRY_FAILURE")
        .contains(reasonCode)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "MANUAL_FLIGHT_REASON_INVALID",
          "Choose a supported reason for the manual flight declaration.");
    }
    if (durationMinutes < 1 || durationMinutes > 720 || notes == null || notes.isBlank()) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "MANUAL_FLIGHT_DECLARATION_INVALID",
          "Enter a duration from 1 to 720 minutes and a non-empty field note.");
    }
    if (maxAltitudeMeters != null
        && (!Double.isFinite(maxAltitudeMeters)
            || maxAltitudeMeters < 0
            || maxAltitudeMeters > 10000)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "MANUAL_FLIGHT_DECLARATION_INVALID",
          "Maximum altitude must be between 0 and 10000 meters.");
    }
    String note =
        "Manual flight declaration (no telemetry). Reason="
            + reasonCode
            + "; estimated duration="
            + durationMinutes
            + " minutes"
            + (maxAltitudeMeters == null
                ? ""
                : "; estimated maximum altitude=" + maxAltitudeMeters + " m")
            + "; Inspector note="
            + notes.trim();
    scope
        .session()
        .recordFlightReport(
            "MANUAL_DECLARATION",
            durationMinutes * 60,
            null,
            maxAltitudeMeters,
            null,
            null,
            "UNKNOWN",
            note,
            "[]");
    sessions.saveAndFlush(scope.session());
    return toPersistedFlightReport(scope.session());
  }

  @Transactional(readOnly = true)
  public SmartDroneHubPort.Telemetry telemetry(UUID actorId, UUID inspectionId, UUID sessionId) {
    Scope scope = requireInspectorSession(actorId, inspectionId, sessionId);
    if (!scope.session().isOpen()) {
      throw failure(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_IN_PROGRESS",
          "Live telemetry is available only while the Inspector session is active.");
    }
    return hub.missionTelemetry(inspectionId, sessionId);
  }

  @Transactional
  public SmartDroneHubPort.FlightReport flightReport(
      UUID actorId, UUID inspectionId, UUID sessionId) {
    Scope scope = requireReadableSession(actorId, inspectionId, sessionId);
    FieldSession session = scope.session();
    if (session.isOpen()) {
      throw failure(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_COMPLETED",
          "Flight report is available after the active field session is completed or aborted.");
    }
    if (session.hasPersistedFlightReport()
        && (session.getTelemetrySource().startsWith("IMPORTED_LOG_")
            || "MANUAL_DECLARATION".equals(session.getTelemetrySource())
            || (session.getFlightTraceJson() != null
                && !session.getFlightTraceJson().isBlank()
                && !"[]".equals(session.getFlightTraceJson())))) {
      return toPersistedFlightReport(session);
    }
    try {
      SmartDroneHubPort.FlightReport report =
          hub.flightReport(inspectionId, sessionId, session.getEndedAt());
      java.util.List<SmartDroneHubPort.FlightTracePoint> trace =
          report.flightTrace() != null ? report.flightTrace() : java.util.List.of();
      session.recordFlightReport(
          report.telemetrySource(),
          report.durationSeconds(),
          report.distanceFlownMeters(),
          report.maxAltitudeMeters(),
          report.startBatteryPercent(),
          report.endBatteryPercent(),
          report.finalState(),
          report.note(),
          serializeTrace(trace));
      sessions.saveAndFlush(session);
      return toPersistedFlightReport(session);
    } catch (RuntimeException ex) {
      Instant started = session.getStartedAt() != null ? session.getStartedAt() : Instant.now();
      Instant ended = session.getEndedAt() != null ? session.getEndedAt() : Instant.now();
      int durationSec = (int) Math.max(0L, Duration.between(started, ended).getSeconds());
      String finalState =
          FieldSession.ABORTED.equals(session.getStatus()) ? "ABORTED" : "NO_FLIGHT";
      String note = "No MAVLink flight telemetry was recorded for this field session.";
      return new SmartDroneHubPort.FlightReport(
          inspectionId,
          sessionId,
          "NONE",
          started,
          ended,
          durationSec,
          0.0,
          null,
          null,
          null,
          finalState,
          note,
          java.util.List.of(),
          session.getFlightVideoEvidenceId());
    }
  }

  @Transactional
  public EvidenceResponse syncFlightVideo(UUID actorId, UUID inspectionId, UUID sessionId) {
    Scope scope = requireInspectorSession(actorId, inspectionId, sessionId);
    FieldSession session = scope.session();
    if (evidenceService == null) {
      throw failure(
          HttpStatus.SERVICE_UNAVAILABLE,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence service is not available.");
    }
    if (session.getFlightVideoEvidenceId() != null) {
      UUID existingId = session.getFlightVideoEvidenceId();
      var existing =
          evidenceService.list(actorId, inspectionId).stream()
              .filter(item -> existingId.equals(item.id()) && !item.isDeleted())
              .findFirst();
      if (existing.isPresent()) {
        return existing.get();
      }
    }
    SmartDroneHubPort.CapturedMedia recording = hub.missionRecording(inspectionId, sessionId);
    EvidenceResponse saved =
        evidenceService.storeCapturedMedia(
            actorId,
            inspectionId,
            sessionId,
            recording.fileName(),
            recording.contentType(),
            recording.content(),
            recording.capturedAt(),
            recording.latitude(),
            recording.longitude(),
            recording.locationDescription());
    session.recordFlightVideoEvidenceId(saved.id());
    sessions.saveAndFlush(session);
    return saved;
  }

  @Transactional
  public EvidenceResponse captureSnapshot(UUID actorId, UUID inspectionId, UUID sessionId) {
    requireInspectorSession(actorId, inspectionId, sessionId);
    if (evidenceService == null) {
      throw failure(
          HttpStatus.SERVICE_UNAVAILABLE,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence service is not available.");
    }
    SmartDroneHubPort.CapturedMedia snapshot = hub.captureSnapshot(inspectionId, sessionId);
    return evidenceService.storeCapturedMedia(
        actorId,
        inspectionId,
        sessionId,
        snapshot.fileName(),
        snapshot.contentType(),
        snapshot.content(),
        snapshot.capturedAt(),
        snapshot.latitude(),
        snapshot.longitude(),
        snapshot.locationDescription());
  }

  private static SmartDroneHubPort.FlightReport toPersistedFlightReport(FieldSession session) {
    List<SmartDroneHubPort.FlightTracePoint> trace = deserializeTrace(session.getFlightTraceJson());
    String source = session.getTelemetrySource();
    boolean isImported = source != null && source.startsWith("IMPORTED_LOG_");
    boolean isManual = "MANUAL_DECLARATION".equals(source);
    Instant startedAt = session.getStartedAt();
    Instant endedAt = session.getEndedAt();
    if (isManual) {
      startedAt = null;
      endedAt = null;
    } else if (isImported) {
      var timed =
          trace.stream()
              .map(SmartDroneHubPort.FlightTracePoint::recordedAt)
              .filter(java.util.Objects::nonNull)
              .toList();
      startedAt = timed.size() >= 2 ? timed.getFirst() : null;
      endedAt = timed.size() >= 2 ? timed.getLast() : null;
    }
    return new SmartDroneHubPort.FlightReport(
        session.getInspectionId(),
        session.getId(),
        source,
        startedAt,
        endedAt,
        session.getFlightDurationSeconds(),
        session.getDistanceFlownMeters() != null
            ? session.getDistanceFlownMeters().doubleValue()
            : null,
        session.getMaxAltitudeMeters() != null
            ? session.getMaxAltitudeMeters().doubleValue()
            : null,
        session.getStartBatteryPercent(),
        session.getEndBatteryPercent(),
        session.getFlightFinalState() != null ? session.getFlightFinalState() : "UNKNOWN",
        session.getFlightReportNote() != null ? session.getFlightReportNote() : "",
        trace,
        session.getFlightVideoEvidenceId());
  }

  private Scope requireReadableSession(UUID actorId, UUID inspectionId, UUID sessionId) {
    ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () -> failure(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found."));
    boolean isReviewer =
        actor.hasRole(Roles.MANAGER) || actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.CUSTOMER);
    if (!isReviewer) {
      return requireInspectorSession(actorId, inspectionId, sessionId);
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found."));
    if (actor.ownerCustomerId() != null
        && !actor.ownerCustomerId().equals(inspection.getOwnerCustomerId())) {
      throw failure(
          HttpStatus.FORBIDDEN,
          "DRONEHUB_SCOPE_DENIED",
          "Access denied to this customer's flight report.");
    }
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "FIELD_SESSION_NOT_FOUND",
                        "Field session not found."));
    return new Scope(inspection, session);
  }

  private static String serializeTrace(java.util.List<SmartDroneHubPort.FlightTracePoint> trace) {
    if (trace == null || trace.isEmpty()) {
      return "[]";
    }
    try {
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(trace);
    } catch (RuntimeException ex) {
      throw failure(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "FLIGHT_TRACE_SERIALIZATION_FAILED",
          "The flight trace could not be saved.");
    }
  }

  private static java.util.List<SmartDroneHubPort.FlightTracePoint> deserializeTrace(String json) {
    if (json == null || json.isBlank()) {
      return java.util.List.of();
    }
    try {
      tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
      return mapper.readValue(
          json,
          mapper
              .getTypeFactory()
              .constructCollectionType(
                  java.util.List.class, SmartDroneHubPort.FlightTracePoint.class));
    } catch (RuntimeException ex) {
      return java.util.List.of();
    }
  }

  private Scope requireInspectorSession(UUID actorId, UUID inspectionId, UUID sessionId) {
    ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .filter(user -> user.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.FORBIDDEN,
                        "DRONEHUB_SCOPE_DENIED",
                        "Only the assigned active Inspector may access mission telemetry."));
    Inspection inspection =
        inspections
            .findAssignedByInspector(inspectionId, actorId)
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found."));
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "FIELD_SESSION_NOT_FOUND",
                        "Field session not found."));
    return new Scope(inspection, session);
  }

  private static String normalizeTelemetrySource(String telemetrySource) {
    if (telemetrySource == null || telemetrySource.isBlank()) {
      return "AUTO";
    }
    String normalized = telemetrySource.trim().toUpperCase(java.util.Locale.ROOT);
    if (!normalized.equals("AUTO")
        && !normalized.equals("MAVLINK")
        && !normalized.equals("SIMULATION")) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "DRONEHUB_SOURCE_INVALID",
          "Telemetry source must be AUTO, MAVLINK, or SIMULATION.");
    }
    return normalized;
  }

  private static void requireActiveFlightSession(Scope scope) {
    if (!scope.inspection().hasFlightPermit()) {
      throw failure(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_REQUIRED",
          "Upload the mission flight permit before recording flight data.");
    }
    if (scope.inspection().getStatus() != InspectionStatus.IN_PROGRESS
        || !scope.session().isInProgress()) {
      throw failure(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_IN_PROGRESS",
          "The field session must be active before recording flight data.");
    }
  }

  private static String extension(String fileName) {
    if (fileName == null) return "";
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static String safeFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) return "uploaded log";
    String sanitized = fileName.replaceAll("[\\r\\n]", "").replaceAll("[/\\\\]", "_");
    return sanitized.substring(0, Math.min(120, sanitized.length()));
  }

  private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double earth = 6_371_000d;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    return 2 * earth * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }

  private record Scope(Inspection inspection, FieldSession session) {}
}
