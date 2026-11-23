package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.AbortFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.CreateFieldSessionFlightRequest;
import com.smartdroneinspection.inspections.api.dto.request.EndFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.ManualFlightDeclarationRequest;
import com.smartdroneinspection.inspections.api.dto.request.StartFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.FieldSessionFlightResponse;
import com.smartdroneinspection.inspections.api.dto.response.FieldSessionResponse;
import com.smartdroneinspection.inspections.api.dto.response.SmartDroneHubFlightReportResponse;
import com.smartdroneinspection.inspections.api.dto.response.SmartDroneHubMissionResponse;
import com.smartdroneinspection.inspections.api.dto.response.SmartDroneHubTelemetryResponse;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.service.InspectionDroneHubService;
import com.smartdroneinspection.inspections.service.InspectionFieldSessionService;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/field-sessions")
public class InspectionFieldSessionController {

  private final InspectionFieldSessionService fieldSessions;
  private final InspectionDroneHubService droneHub;

  public InspectionFieldSessionController(
      InspectionFieldSessionService fieldSessions, InspectionDroneHubService droneHub) {
    this.fieldSessions = fieldSessions;
    this.droneHub = droneHub;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN', 'CUSTOMER')")
  public ApiResponse<List<FieldSessionResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    UUID actorId = subject(jwt);
    List<FieldSession> list = fieldSessions.listSessions(actorId, inspectionId);
    List<FieldSessionFlightResponse> allFlights =
        droneHub != null ? droneHub.listFlightsForInspection(actorId, inspectionId) : List.of();
    Map<UUID, List<FieldSessionFlightResponse>> flightsBySession =
        allFlights == null
            ? Map.of()
            : allFlights.stream()
                .collect(Collectors.groupingBy(FieldSessionFlightResponse::fieldSessionId));
    return ApiResponse.success(
        list.stream()
            .map(
                session ->
                    response(session, flightsBySession.getOrDefault(session.getId(), List.of())))
            .toList());
  }

  @PostMapping
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> start(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody StartFieldSessionRequest request) {
    boolean hasStructuredPreFlight =
        request.sessionDate() != null
            || (request.preFlightChecklistSnapshot() != null
                && !request.preFlightChecklistSnapshot().isEmpty())
            || request.preFlightConditions() != null;
    FieldSession started =
        hasStructuredPreFlight
            ? fieldSessions.start(
                subject(jwt),
                inspectionId,
                request.preFlightChecklistNote(),
                Boolean.TRUE.equals(request.startNow()),
                request.sessionDate(),
                request.preFlightChecklistSnapshot(),
                request.preFlightConditions())
            : fieldSessions.start(
                subject(jwt),
                inspectionId,
                request.preFlightChecklistNote(),
                Boolean.TRUE.equals(request.startNow()));
    return ApiResponse.success(response(started, List.of()));
  }

  @PostMapping("/{sessionId}/completion")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> complete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody EndFieldSessionRequest request) {
    UUID actorId = subject(jwt);
    boolean hasStructuredPostFlight =
        request.postFlightChecklistSnapshot() != null
            && !request.postFlightChecklistSnapshot().isEmpty();
    FieldSession completed =
        hasStructuredPostFlight
            ? fieldSessions.complete(
                actorId,
                inspectionId,
                sessionId,
                request.outcomeNotes(),
                request.postFlightChecklistSnapshot())
            : fieldSessions.complete(actorId, inspectionId, sessionId, request.outcomeNotes());
    List<FieldSessionFlightResponse> sessionFlights =
        droneHub != null
            ? droneHub.listFlightsForSession(actorId, inspectionId, sessionId)
            : List.of();
    return ApiResponse.success(
        response(completed, sessionFlights != null ? sessionFlights : List.of()));
  }

  @GetMapping("/{sessionId}/flights")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN', 'CUSTOMER')")
  public ApiResponse<List<FieldSessionFlightResponse>> listSessionFlights(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(
        droneHub.listFlightsForSession(subject(jwt), inspectionId, sessionId));
  }

  @PostMapping("/{sessionId}/flights")
  @PreAuthorize("hasRole('INSPECTOR')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FieldSessionFlightResponse> createFlight(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody(required = false) CreateFieldSessionFlightRequest request) {
    return ApiResponse.success(
        droneHub.createFlight(subject(jwt), inspectionId, sessionId, request));
  }

  @DeleteMapping("/{sessionId}/flights/{flightId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFlight(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @PathVariable UUID flightId) {
    droneHub.deleteFlight(subject(jwt), inspectionId, sessionId, flightId);
  }

  @PostMapping("/{sessionId}/drone-hub/start")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubMissionResponse> startDroneHubMission(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(defaultValue = "AUTO") String telemetrySource,
      @RequestParam(required = false) UUID flightId) {
    SmartDroneHubPort.Mission mission =
        flightId != null
            ? droneHub.start(subject(jwt), inspectionId, sessionId, telemetrySource, flightId)
            : droneHub.start(subject(jwt), inspectionId, sessionId, telemetrySource);
    return ApiResponse.success(missionResponse(mission));
  }

  @PostMapping("/{sessionId}/drone-hub/complete")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubFlightReportResponse> completeDroneHubFlight(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId) {
    SmartDroneHubPort.FlightReport report =
        flightId != null
            ? droneHub.completeFlight(subject(jwt), inspectionId, sessionId, flightId)
            : droneHub.completeFlight(subject(jwt), inspectionId, sessionId);
    return ApiResponse.success(flightReportResponse(report));
  }

  @PostMapping("/{sessionId}/drone-hub/import-log")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubFlightReportResponse> importFlightLog(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId,
      @RequestParam(required = false) java.time.LocalDate flightDate,
      @RequestParam(required = false) String flightLabel,
      @RequestPart("file") MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 15L * 1024 * 1024) {
      throw new com.smartdroneinspection.shared.exception.BusinessException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "FLIGHT_LOG_SIZE_INVALID",
          "Select a non-empty flight log no larger than 15 MB.");
    }
    try {
      SmartDroneHubPort.FlightReport report =
          (flightId != null
                  || flightDate != null
                  || (flightLabel != null && !flightLabel.isBlank()))
              ? droneHub.importFlightLog(
                  subject(jwt),
                  inspectionId,
                  sessionId,
                  flightId,
                  file.getOriginalFilename(),
                  file.getInputStream(),
                  flightDate,
                  flightLabel)
              : droneHub.importFlightLog(
                  subject(jwt),
                  inspectionId,
                  sessionId,
                  file.getOriginalFilename(),
                  file.getInputStream());
      return ApiResponse.success(flightReportResponse(report));
    } catch (java.io.IOException ex) {
      throw new com.smartdroneinspection.shared.exception.BusinessException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "FLIGHT_LOG_READ_FAILED",
          "The uploaded flight log could not be read.");
    }
  }

  @PostMapping("/{sessionId}/drone-hub/manual-declaration")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubFlightReportResponse> declareManualFlight(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId,
      @Valid @RequestBody ManualFlightDeclarationRequest request) {
    SmartDroneHubPort.FlightReport report =
        (flightId != null || request.flightDate() != null || request.flightLabel() != null)
            ? droneHub.declareManualFlight(
                subject(jwt),
                inspectionId,
                sessionId,
                flightId,
                request.reasonCode(),
                request.estimatedDurationMinutes(),
                request.maxAltitudeMeters(),
                request.notes(),
                request.flightDate(),
                request.flightLabel())
            : droneHub.declareManualFlight(
                subject(jwt),
                inspectionId,
                sessionId,
                request.reasonCode(),
                request.estimatedDurationMinutes(),
                request.maxAltitudeMeters(),
                request.notes());
    return ApiResponse.success(flightReportResponse(report));
  }

  @GetMapping("/{sessionId}/drone-hub/telemetry")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubTelemetryResponse> droneHubTelemetry(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId) {
    SmartDroneHubPort.Telemetry telemetry =
        flightId != null
            ? droneHub.telemetry(subject(jwt), inspectionId, sessionId, flightId)
            : droneHub.telemetry(subject(jwt), inspectionId, sessionId);
    return ApiResponse.success(telemetryResponse(telemetry));
  }

  @GetMapping("/{sessionId}/drone-hub/flight-report")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN', 'CUSTOMER')")
  public ApiResponse<SmartDroneHubFlightReportResponse> droneHubFlightReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId) {
    SmartDroneHubPort.FlightReport report =
        flightId != null
            ? droneHub.flightReport(subject(jwt), inspectionId, sessionId, flightId)
            : droneHub.flightReport(subject(jwt), inspectionId, sessionId);
    return ApiResponse.success(flightReportResponse(report));
  }

  @PostMapping("/{sessionId}/drone-hub/sync-video")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> syncDroneHubFlightVideo(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId) {
    return ApiResponse.success(
        flightId != null
            ? droneHub.syncFlightVideo(subject(jwt), inspectionId, sessionId, flightId)
            : droneHub.syncFlightVideo(subject(jwt), inspectionId, sessionId));
  }

  @PostMapping("/{sessionId}/drone-hub/capture")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> captureDroneHubFrame(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(required = false) UUID flightId) {
    return ApiResponse.success(
        flightId != null
            ? droneHub.captureSnapshot(subject(jwt), inspectionId, sessionId, flightId)
            : droneHub.captureSnapshot(subject(jwt), inspectionId, sessionId));
  }

  private static SmartDroneHubMissionResponse missionResponse(SmartDroneHubPort.Mission value) {
    return new SmartDroneHubMissionResponse(
        value.inspectionId(),
        value.fieldSessionId(),
        value.missionState(),
        value.telemetrySource(),
        value.connected(),
        value.lastUpdateAt(),
        value.latitude(),
        value.longitude(),
        value.altitudeMeters(),
        value.groundSpeedMps(),
        value.headingDeg(),
        value.batteryPercent(),
        value.flightMode(),
        value.armed(),
        value.currentWaypoint(),
        value.distanceFlownMeters());
  }

  private static SmartDroneHubTelemetryResponse telemetryResponse(
      SmartDroneHubPort.Telemetry value) {
    return new SmartDroneHubTelemetryResponse(
        value.inspectionId(),
        value.fieldSessionId(),
        value.missionState(),
        value.telemetrySource(),
        value.connected(),
        value.lastUpdateAt(),
        value.latitude(),
        value.longitude(),
        value.altitudeMeters(),
        value.groundSpeedMps(),
        value.headingDeg(),
        value.batteryPercent(),
        value.flightMode(),
        value.armed(),
        value.currentWaypoint(),
        value.distanceFlownMeters());
  }

  private static SmartDroneHubFlightReportResponse flightReportResponse(
      SmartDroneHubPort.FlightReport value) {
    return new SmartDroneHubFlightReportResponse(
        value.inspectionId(),
        value.fieldSessionId(),
        value.telemetrySource(),
        value.startedAt(),
        value.endedAt(),
        value.durationSeconds(),
        value.distanceFlownMeters(),
        value.maxAltitudeMeters(),
        value.startBatteryPercent(),
        value.endBatteryPercent(),
        value.finalState(),
        value.note(),
        value.flightTrace() != null ? value.flightTrace() : List.of(),
        value.flightVideoEvidenceId());
  }

  @PostMapping("/{sessionId}/abort")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> abort(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody AbortFieldSessionRequest request) {
    return ApiResponse.success(
        response(fieldSessions.abort(subject(jwt), inspectionId, sessionId, request.reason())));
  }

  @PostMapping("/{sessionId}/force-abort")
  @PreAuthorize("hasRole('MANAGER')")
  public ApiResponse<FieldSessionResponse> forceAbort(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody AbortFieldSessionRequest request) {
    return ApiResponse.success(
        response(
            fieldSessions.forceAbort(subject(jwt), inspectionId, sessionId, request.reason())));
  }

  private static FieldSessionResponse response(FieldSession session) {
    return response(session, List.of());
  }

  private static FieldSessionResponse response(
      FieldSession session, List<FieldSessionFlightResponse> flights) {
    return new FieldSessionResponse(
        session.getId(),
        session.getInspectionId(),
        session.getStatus(),
        session.getPreFlightChecklistNote(),
        session.getStartedAt(),
        session.getEndedAt(),
        session.getPostponementReason(),
        session.getAbortReason(),
        session.getOutcomeNotes(),
        session.getFlightVideoEvidenceId(),
        session.getTelemetrySource(),
        session.hasFlightReport(),
        session.getSessionDate(),
        session.getPreFlightChecklistSnapshot(),
        session.getPreFlightConditions(),
        session.getPostFlightChecklistSnapshot(),
        flights);
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
