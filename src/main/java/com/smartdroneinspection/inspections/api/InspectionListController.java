package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.response.InspectionPageResponse;
import com.smartdroneinspection.inspections.service.InspectionListService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  public InspectionListController(InspectionListService inspectionList) {
    this.inspectionList = inspectionList;
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
