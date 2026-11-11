package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.users.api.dto.request.SavePilotLicenseClassRequest;
import com.smartdroneinspection.users.api.dto.response.PilotLicenseClassResponse;
import com.smartdroneinspection.users.service.PilotLicenseClassService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pilot-license-classes")
public class PilotLicenseClassController {

  private final PilotLicenseClassService service;

  public PilotLicenseClassController(PilotLicenseClassService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("permitAll()")
  public ApiResponse<List<PilotLicenseClassResponse>> listActive() {
    return ApiResponse.success(service.listActive());
  }

  @GetMapping("/admin")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<List<PilotLicenseClassResponse>> listAll() {
    return ApiResponse.success(service.listAll());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PilotLicenseClassResponse> create(
      @Valid @RequestBody SavePilotLicenseClassRequest request) {
    return ApiResponse.success(service.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PilotLicenseClassResponse> update(
      @PathVariable UUID id, @Valid @RequestBody SavePilotLicenseClassRequest request) {
    return ApiResponse.success(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }
}
