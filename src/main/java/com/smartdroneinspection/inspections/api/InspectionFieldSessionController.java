package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.AbortFieldSessionRequest;
import com.smartdroneinspection.inspections.api.dto.request.EndFieldSessionRequest;
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

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/field-sessions")
public class InspectionFieldSessionController {

  private final InspectionFieldSessionService fieldSessions;

  public InspectionFieldSessionController(InspectionFieldSessionService fieldSessions) {
    this.fieldSessions = fieldSessions;
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
        session.getOutcomeNotes());
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
