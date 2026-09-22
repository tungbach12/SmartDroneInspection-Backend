package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.api.dto.request.StartInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.response.ChecklistResponseResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionAssignmentResponse;
import com.smartdroneinspection.inspections.api.dto.response.StartInspectionResponse;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.shared.exception.BusinessException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspections")
@PreAuthorize("hasRole('INSPECTOR')")
public class InspectionController {

  private final InspectionService inspections;

  public InspectionController(InspectionService inspections) {
    this.inspections = inspections;
  }

  @GetMapping("/assignments")
  public List<InspectionAssignmentResponse> assignments(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "ACCEPTED") InspectionAssignmentStatus status) {
    if (status != InspectionAssignmentStatus.ACCEPTED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Only accepted assignments are available to the inspection client.");
    }
    return inspections.listAcceptedAssignments(subject(jwt));
  }

  @PostMapping("/start")
  public StartInspectionResponse start(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartInspectionRequest request) {
    return inspections.start(subject(jwt), request.assignmentId());
  }

  @PutMapping("/{inspectionId}/checklist-responses/{checklistItemId}")
  public ChecklistResponseResponse saveChecklistResponse(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID checklistItemId,
      @Valid @RequestBody ChecklistResponseRequest request) {
    return inspections.saveChecklistResponse(subject(jwt), inspectionId, checklistItemId, request);
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
