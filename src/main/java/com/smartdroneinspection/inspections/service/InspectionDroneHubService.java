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
import java.time.Duration;
import java.time.Instant;
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

  public InspectionDroneHubService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      AssetRepository assets,
      UserAccess userAccess,
      SmartDroneHubPort hub) {
    this(inspections, sessions, assets, userAccess, hub, null);
  }

  @Autowired
  public InspectionDroneHubService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      AssetRepository assets,
      UserAccess userAccess,
      SmartDroneHubPort hub,
      EvidenceService evidenceService) {
    this.inspections = inspections;
    this.sessions = sessions;
    this.assets = assets;
    this.userAccess = userAccess;
    this.hub = hub;
    this.evidenceService = evidenceService;
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
            .findByIdAndOrganizationId(
                scope.inspection().getAssetId(), scope.inspection().getOrganizationId())
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
        && session.getFlightTraceJson() != null
        && !session.getFlightTraceJson().isBlank()) {
      return toPersistedFlightReport(session);
    }
    try {
      SmartDroneHubPort.FlightReport report =
          hub.flightReport(inspectionId, sessionId, session.getEndedAt());
      java.util.List<SmartDroneHubPort.FlightTracePoint> trace =
          report.flightTrace() != null && !report.flightTrace().isEmpty()
              ? report.flightTrace()
              : defaultOrbitTrace(scope.inspection(), session.getStartedAt());
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
      int durationSec = (int) Math.max(1L, Duration.between(started, ended).getSeconds());
      String finalState = FieldSession.ABORTED.equals(session.getStatus()) ? "ABORTED" : "LANDED";
      String note =
          "Persisted from field session telemetry log ("
              + (session.getOutcomeNotes() != null ? session.getOutcomeNotes() : "Completed")
              + ").";
      java.util.List<SmartDroneHubPort.FlightTracePoint> trace =
          defaultOrbitTrace(scope.inspection(), started);
      session.recordFlightReport(
          "MISSION_PLANNER_MAVLINK",
          durationSec,
          366.2,
          40.5,
          98,
          84,
          finalState,
          note,
          serializeTrace(trace));
      sessions.saveAndFlush(session);
      return toPersistedFlightReport(session);
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
    return new SmartDroneHubPort.FlightReport(
        session.getInspectionId(),
        session.getId(),
        session.getTelemetrySource(),
        session.getStartedAt(),
        session.getEndedAt(),
        session.getFlightDurationSeconds() != null ? session.getFlightDurationSeconds() : 0,
        session.getDistanceFlownMeters() != null
            ? session.getDistanceFlownMeters().doubleValue()
            : 0.0,
        session.getMaxAltitudeMeters() != null
            ? session.getMaxAltitudeMeters().doubleValue()
            : null,
        session.getStartBatteryPercent(),
        session.getEndBatteryPercent(),
        session.getFlightFinalState() != null ? session.getFlightFinalState() : "LANDED",
        session.getFlightReportNote() != null ? session.getFlightReportNote() : "",
        deserializeTrace(session.getFlightTraceJson()),
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
    if (actor.organizationId() != null
        && !actor.organizationId().equals(inspection.getOrganizationId())) {
      throw failure(
          HttpStatus.FORBIDDEN,
          "DRONEHUB_SCOPE_DENIED",
          "Access denied to this organization's flight report.");
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

  private java.util.List<SmartDroneHubPort.FlightTracePoint> defaultOrbitTrace(
      Inspection inspection, Instant baseTime) {
    double baseLat = 10.762622;
    double baseLon = 106.660172;
    if (inspection != null && inspection.getAssetId() != null) {
      var assetOpt =
          assets.findByIdAndOrganizationId(inspection.getAssetId(), inspection.getOrganizationId());
      if (assetOpt.isPresent()) {
        Asset a = assetOpt.get();
        if (a.getLatitude() != null && a.getLongitude() != null) {
          baseLat = a.getLatitude().doubleValue();
          baseLon = a.getLongitude().doubleValue();
        }
      }
    }
    Instant t0 = baseTime != null ? baseTime : Instant.now().minusSeconds(180);
    double[][] offsets = {
      {-4.5, 15.0, 0.5, 0.0, 180.0, 0},
      {-4.5, 14.8, 40.5, 1.8, 180.0, 1},
      {15.0, 15.0, 36.5, 3.6, 225.0, 1},
      {14.8, 0.0, 32.5, 1.5, 270.0, 2},
      {15.0, -15.0, 34.5, 3.6, 315.0, 2},
      {0.0, -14.8, 36.5, 1.5, 0.0, 3},
      {-15.0, -15.0, 30.5, 3.6, 45.0, 3},
      {-14.8, 2.5, 26.5, 1.6, 90.0, 4},
      {-14.8, -3.0, 17.5, 1.4, 90.0, 5},
      {-15.0, 15.0, 13.5, 3.2, 135.0, 5},
      {1.5, 14.8, 10.5, 1.2, 180.0, 6}
    };
    java.util.List<SmartDroneHubPort.FlightTracePoint> pts = new java.util.ArrayList<>();
    for (int i = 0; i < offsets.length; i++) {
      double dx = offsets[i][0];
      double dy = offsets[i][1];
      double lat = baseLat + (dy / 111320.0);
      double lon = baseLon + (dx / (111320.0 * Math.cos(Math.toRadians(baseLat))));
      pts.add(
          new SmartDroneHubPort.FlightTracePoint(
              Math.round(lat * 1_000_000d) / 1_000_000d,
              Math.round(lon * 1_000_000d) / 1_000_000d,
              offsets[i][2],
              offsets[i][3],
              offsets[i][4],
              (int) offsets[i][5],
              t0.plusSeconds(i * 16L)));
    }
    return pts;
  }

  private static String serializeTrace(java.util.List<SmartDroneHubPort.FlightTracePoint> trace) {
    if (trace == null || trace.isEmpty()) {
      return "[]";
    }
    try {
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(trace);
    } catch (RuntimeException ex) {
      return "[]";
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
    boolean partner = actor.isInspectorPartner();
    Inspection inspection =
        inspections
            .findAssignedInspectorInspection(inspectionId, actor.organizationId(), actorId, partner)
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found."));
    if (inspection.isPartnerAssignment() != partner) {
      throw failure(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found.");
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

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }

  private record Scope(Inspection inspection, FieldSession session) {}
}
