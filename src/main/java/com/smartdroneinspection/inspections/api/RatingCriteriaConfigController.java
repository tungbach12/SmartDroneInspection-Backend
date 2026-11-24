package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.CreateRatingCriterionRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateRatingCriterionRequest;
import com.smartdroneinspection.inspections.api.dto.response.RatingCriterionConfigResponse;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.inspections.service.RatingCriteriaConfigService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin-configurable post-inspection rating criteria endpoints and public active lookup. */
@RestController
@RequestMapping("/api/v1")
public class RatingCriteriaConfigController {

  private final RatingCriteriaConfigService service;

  public RatingCriteriaConfigController(RatingCriteriaConfigService service) {
    this.service = service;
  }

  @GetMapping("/ratings/criteria")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<RatingCriterionConfigResponse>> listPublic(
      @RequestParam(required = false) RaterRole raterRole,
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return ApiResponse.success(service.list(raterRole, includeInactive));
  }

  @GetMapping("/admin/rating-criteria")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<List<RatingCriterionConfigResponse>> listAdmin(
      @RequestParam(required = false) RaterRole raterRole) {
    return ApiResponse.success(service.list(raterRole, true));
  }

  @PostMapping("/admin/rating-criteria")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<RatingCriterionConfigResponse> create(
      @Valid @RequestBody CreateRatingCriterionRequest request) {
    return ApiResponse.success(service.create(request));
  }

  @PutMapping("/admin/rating-criteria/{id}")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<RatingCriterionConfigResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateRatingCriterionRequest request) {
    return ApiResponse.success(service.update(id, request));
  }

  @PatchMapping("/admin/rating-criteria/{id}/toggle-active")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<RatingCriterionConfigResponse> toggleActive(@PathVariable UUID id) {
    return ApiResponse.success(service.toggleActive(id));
  }

  @DeleteMapping("/admin/rating-criteria/{id}")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
