package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.AbortFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.EndFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.ManualFlightDeclarationRequest;
import com.smartdroneinspection.inspections.api.dto.request.StartFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
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
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
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
    return ApiResponse.success(
        fieldSessions.listSessions(subject(jwt), inspectionId).stream()
            .map(InspectionFieldSessionController::response)
            .toList());
  }

  @PostMapping
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> start(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody StartFieldSessionRequest request) {
    return ApiResponse.success(
        response(
            fieldSessions.start(subject(jwt), inspectionId, request.preFlightChecklistNote())));
  }

  @PostMapping("/{sessionId}/completion")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> complete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody EndFieldSessionRequest request) {
    return ApiResponse.success(
        response(
            fieldSessions.complete(subject(jwt), inspectionId, sessionId, request.outcomeNotes())));
  }

  @PostMapping("/{sessionId}/drone-hub/start")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubMissionResponse> startDroneHubMission(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestParam(defaultValue = "AUTO") String telemetrySource) {
    return ApiResponse.success(
        missionResponse(droneHub.start(subject(jwt), inspectionId, sessionId, telemetrySource)));
  }

  @PostMapping("/{sessionId}/drone-hub/import-log")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubFlightReportResponse> importFlightLog(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @RequestPart("file") MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 15L * 1024 * 1024) {
      throw new com.smartdroneinspection.shared.exception.BusinessException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "FLIGHT_LOG_SIZE_INVALID",
          "Select a non-empty flight log no larger than 15 MB.");
    }
    try {
      return ApiResponse.success(
          flightReportResponse(
              droneHub.importFlightLog(
                  subject(jwt),
                  inspectionId,
                  sessionId,
                  file.getOriginalFilename(),
                  file.getInputStream())));
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
      @Valid @RequestBody ManualFlightDeclarationRequest request) {
    return ApiResponse.success(
        flightReportResponse(
            droneHub.declareManualFlight(
                subject(jwt),
                inspectionId,
                sessionId,
                request.reasonCode(),
                request.estimatedDurationMinutes(),
                request.maxAltitudeMeters(),
                request.notes())));
  }

  @GetMapping("/{sessionId}/drone-hub/telemetry")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<SmartDroneHubTelemetryResponse> droneHubTelemetry(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(
        telemetryResponse(droneHub.telemetry(subject(jwt), inspectionId, sessionId)));
  }

  @GetMapping("/{sessionId}/drone-hub/flight-report")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN', 'CUSTOMER')")
  public ApiResponse<SmartDroneHubFlightReportResponse> droneHubFlightReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(
        flightReportResponse(droneHub.flightReport(subject(jwt), inspectionId, sessionId)));
  }

  @PostMapping("/{sessionId}/drone-hub/sync-video")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> syncDroneHubFlightVideo(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(droneHub.syncFlightVideo(subject(jwt), inspectionId, sessionId));
  }

  @PostMapping("/{sessionId}/drone-hub/capture")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> captureDroneHubFrame(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(droneHub.captureSnapshot(subject(jwt), inspectionId, sessionId));
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

  private static FieldSessionResponse response(FieldSession session) {
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
        session.hasFlightReport());
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
