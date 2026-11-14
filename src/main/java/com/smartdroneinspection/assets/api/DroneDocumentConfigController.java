package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneDocumentConfigRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneDocumentConfigRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneDocumentConfigResponse;
import com.smartdroneinspection.assets.service.DroneDocumentConfigService;
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
@RequestMapping("/api/v1/drone-document-configs")
public class DroneDocumentConfigController {

  private final DroneDocumentConfigService service;

  public DroneDocumentConfigController(DroneDocumentConfigService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<DroneDocumentConfigResponse>> list(
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
    return ApiResponse.success(service.list(includeInactive));
  }

  @PostMapping
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DroneDocumentConfigResponse> create(
      @Valid @RequestBody CreateDroneDocumentConfigRequest request) {
    return ApiResponse.success(service.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<DroneDocumentConfigResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateDroneDocumentConfigRequest request) {
    return ApiResponse.success(service.update(id, request));
  }

  @PatchMapping("/{id}/toggle-active")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<DroneDocumentConfigResponse> toggleActive(@PathVariable UUID id) {
    return ApiResponse.success(service.toggleActive(id));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
