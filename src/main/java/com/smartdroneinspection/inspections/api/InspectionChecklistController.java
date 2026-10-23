package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.api.dto.response.ChecklistResponseResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChecklistItemResponse;
import com.smartdroneinspection.inspections.service.InspectionChecklistService;
import com.smartdroneinspection.shared.RequestLocale;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MF2-11: the checklist the assigned Inspector records against an inspection.
 *
 * <p>The checklist's own text follows the requested locale, so the same inspection reads in the
 * language the Inspector selected. The Inspector's answers and notes are never translated.
 */
@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}")
public class InspectionChecklistController {

  private final InspectionChecklistService checklists;

  public InspectionChecklistController(InspectionChecklistService checklists) {
    this.checklists = checklists;
  }

  @GetMapping("/checklist")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<InspectionChecklistItemResponse>> checklist(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      HttpServletRequest request) {
    return ApiResponse.success(
        checklists.listChecklist(subject(jwt), inspectionId, RequestLocale.from(request)));
  }

  @PutMapping("/checklist-responses/{checklistItemId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<ChecklistResponseResponse> saveResponse(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID checklistItemId,
      @Valid @RequestBody ChecklistResponseRequest request) {
    return ApiResponse.success(
        checklists.saveResponse(subject(jwt), inspectionId, checklistItemId, request));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
