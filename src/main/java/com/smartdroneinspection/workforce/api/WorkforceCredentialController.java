package com.smartdroneinspection.workforce.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.workforce.api.dto.request.CreateWorkforceCredentialRequest;
import com.smartdroneinspection.workforce.api.dto.request.ReviewWorkforceCredentialRequest;
import com.smartdroneinspection.workforce.api.dto.response.WorkforceCredentialResponse;
import com.smartdroneinspection.workforce.service.WorkforceCredentialService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ORG_MANAGER workforce credential records and their internal review. The tenant is taken from the
 * authenticated principal inside the service; no endpoint accepts an organization or actor zone.
 * Review here is an internal human decision and is not an external or government verification.
 */
@RestController
@RequestMapping("/api/v1/workforce/credentials")
@PreAuthorize("hasRole('" + Roles.ORG_MANAGER + "')")
public class WorkforceCredentialController {

  private final WorkforceCredentialService credentials;

  public WorkforceCredentialController(WorkforceCredentialService credentials) {
    this.credentials = credentials;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<WorkforceCredentialResponse>> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateWorkforceCredentialRequest body) {
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(credentials.create(subject(jwt), body)));
  }

  @GetMapping
  public ApiResponse<List<WorkforceCredentialResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(credentials.list(subject(jwt)));
  }

  @PutMapping("/{credentialId}/review")
  public ApiResponse<WorkforceCredentialResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID credentialId,
      @Valid @RequestBody ReviewWorkforceCredentialRequest body) {
    return ApiResponse.success(credentials.review(subject(jwt), credentialId, body));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
