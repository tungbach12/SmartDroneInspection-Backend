package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneFeatureConfigRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneFeatureConfigRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneFeatureConfigResponse;
import com.smartdroneinspection.assets.service.DroneFeatureConfigService;
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

@RestController
@RequestMapping("/api/v1/drone-feature-configs")
public class DroneFeatureConfigController {

  private final DroneFeatureConfigService service;

  public DroneFeatureConfigController(DroneFeatureConfigService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<DroneFeatureConfigResponse>> list(
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
    return ApiResponse.success(service.list(includeInactive));
  }

  @PostMapping
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DroneFeatureConfigResponse> create(
      @Valid @RequestBody CreateDroneFeatureConfigRequest request) {
    return ApiResponse.success(service.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<DroneFeatureConfigResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateDroneFeatureConfigRequest request) {
    return ApiResponse.success(service.update(id, request));
  }

  @PatchMapping("/{id}/toggle-active")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<DroneFeatureConfigResponse> toggleActive(@PathVariable UUID id) {
    return ApiResponse.success(service.toggleActive(id));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
