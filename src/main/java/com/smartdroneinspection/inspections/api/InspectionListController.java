package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPageResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionResponse;
import com.smartdroneinspection.inspections.service.InspectionCreationService;
import com.smartdroneinspection.inspections.service.InspectionListService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inspection and report collections.
 *
 * <p>This is separate from {@link InspectionController} because every route there is nested under
 * {@code /{inspectionId}}, which would make a collection route ambiguous.
 *
 * <p>The route check is navigation policy only. Organization, assignment and platform scope are
 * enforced in the use case, so the right role with the wrong tenant still receives nothing.
 */
@RestController
@RequestMapping("/api/v1/inspections")
public class InspectionListController {

  private final InspectionListService inspectionList;
  private final InspectionCreationService creationService;

  public InspectionListController(
      InspectionListService inspectionList, InspectionCreationService creationService) {
    this.inspectionList = inspectionList;
    this.creationService = creationService;
  }

  @PostMapping
  @PreAuthorize("hasRole('ORG_ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<InspectionResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInspectionRequest request) {
    return ApiResponse.success(creationService.create(UUID.fromString(jwt.getSubject()), request));
  }

  @GetMapping("/{inspectionId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'INSPECTOR')")
  public ApiResponse<InspectionResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(
        creationService.get(UUID.fromString(jwt.getSubject()), inspectionId));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'INSPECTOR')")
  public ApiResponse<InspectionPageResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(
        inspectionList.list(UUID.fromString(jwt.getSubject()), page, pageSize));
  }

  /**
   * The same rows limited to inspections that carry a report. Reports belong to an inspection, so
   * the review queue is a filtered view of this collection rather than a second resource.
   */
  @GetMapping("/with-reports")
  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'INSPECTOR')")
  public ApiResponse<InspectionPageResponse> listWithReports(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(
        inspectionList.listWithReports(UUID.fromString(jwt.getSubject()), page, pageSize));
  }
}
