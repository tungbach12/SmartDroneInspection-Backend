package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.UpsertInspectorRatingRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionRatingBundleResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectorRatingProfileResponse;
import com.smartdroneinspection.inspections.service.InspectionRatingService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Post-inspection Customer & Manager ratings for Inspectors. */
@RestController
@RequestMapping("/api/v1")
public class InspectorRatingController {

  private final InspectionRatingService ratingService;

  public InspectorRatingController(InspectionRatingService ratingService) {
    this.ratingService = ratingService;
  }

  @GetMapping("/inspections/{inspectionId}/inspector-rating")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<InspectionRatingBundleResponse> getInspectionRatings(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(ratingService.getInspectionRatings(actorId(jwt), inspectionId));
  }

  @PutMapping("/inspections/{inspectionId}/inspector-rating")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER')")
  public ApiResponse<InspectionRatingBundleResponse> upsertInspectionRating(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody UpsertInspectorRatingRequest request) {
    return ApiResponse.success(
        ratingService.upsertForInspection(actorId(jwt), inspectionId, request));
  }

  @GetMapping("/inspectors/{inspectorId}/ratings")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<InspectorRatingProfileResponse> getInspectorRatings(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectorId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        ratingService.getInspectorSummaryAndHistory(
            actorId(jwt),
            inspectorId,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))));
  }

  @GetMapping("/inspectors/me/ratings")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectorRatingProfileResponse> getMyRatings(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    UUID me = actorId(jwt);
    return ApiResponse.success(
        ratingService.getInspectorSummaryAndHistory(
            me, me, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
