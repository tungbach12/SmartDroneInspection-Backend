package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.AbortFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.PostponeFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.StartFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.response.FieldSessionResponse;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.service.InspectionFieldSessionService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * MF2-09 to MF2-11: the assigned Inspector's field session, on site.
 *
 * <p>Only {@code INSPECTOR} reaches these routes. An {@code ORG_ADMIN} may approve readiness and
 * read the compliance gate, but the field session is the Inspector's own record of work they
 * performed, so the administrator cannot start or close one on their behalf. The service re-checks
 * that the inspection is assigned to the caller, so the route check is navigation policy only.
 *
 * <p>Starting a session is not Drone actuation. These routes record that the software agreed the
 * paperwork and the pre-flight checklist were in order; nothing here arms the aircraft, and no
 * {@code startedAt} is hardware flight time.
 */
@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/field-sessions")
public class InspectionFieldSessionController {

  private final InspectionFieldSessionService fieldSessions;

  public InspectionFieldSessionController(InspectionFieldSessionService fieldSessions) {
    this.fieldSessions = fieldSessions;
  }

  /** The caller's own sessions for this inspection, oldest first. */
  @GetMapping
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<FieldSessionResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(
        fieldSessions.listSessions(subject(jwt), inspectionId).stream()
            .map(InspectionFieldSessionController::response)
            .toList());
  }

  /** Starts the session after the MF2-10 readiness recheck. */
  @PostMapping
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> start(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody StartFieldSessionRequest request) {
    return ApiResponse.success(
        response(
            fieldSessions.start(
                subject(jwt),
                inspectionId,
                request.checklistTemplateId(),
                request.preFlightChecklistNote())));
  }

  /** Weather or site safety stopped the session; the inspection stays startable (MF2-09). */
  @PostMapping("/{sessionId}/postponement")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FieldSessionResponse> postpone(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody PostponeFieldSessionRequest request) {
    return ApiResponse.success(
        response(fieldSessions.postpone(subject(jwt), inspectionId, sessionId, request.reason())));
  }

  /** The session cannot continue and will not resume under this record (MF2-11). */
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
        session.getOrganizationId(),
        session.getInspectorUserId(),
        session.getDroneId(),
        session.getReadinessDecisionId(),
        session.getChecklistTemplateId(),
        session.getReadinessVersion(),
        session.getStatus(),
        session.getStartedAt(),
        session.getEndedAt(),
        session.getPostponementReason(),
        session.getAbortReason());
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
